#!/usr/bin/env bash
# ============================================================================
# MyOptimize Ultra — ручная сборка одного модуля БЕЗ Gradle/Loom.
#
# Зачем: на слабых машинах (2 ГБ RAM) демон Gradle+Loom погибает от OOM.
# Скрипт делает то же, что `./gradlew :vX:build`, шаг за шагом, каждый шаг —
# отдельный JVM с контролируемой памятью.
#
# Использование:
#   JAVA_HOME=/path/to/jdk21 bash scripts/build-manual.sh v1.21.1
#   JAVA_HOME=/path/to/jdk25 bash scripts/build-manual.sh v26.3   # mojmap, без ремапа
#
# Режимы (берутся из versions/vX/gradle.properties):
#   yarn   — 1.20–1.21.11: скачивает MC, мержит client+server, ремапит
#            official -> intermediary -> named (yarn), ремапит модули Fabric API
#            в named, компилирует, ремапит jar мода named -> intermediary
#            (MixinExtension переписывает строки в mixin-аннотациях).
#   mojmap — 26.x: MC без обфускации; компилирует сразу против mojmap-jar
#            (никакого ремапа), упаковывает.
#
# Выход: versions/vX/build/libs/myoptimize-ultra-<mod_version>+<mc>.jar
# ============================================================================
set -euo pipefail

MODULE="${1:?usage: JAVA_HOME=... bash scripts/build-manual.sh v1.21.1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/versions/$MODULE/gradle.properties"
[ -f "$PROPS" ] || { echo "нет $PROPS"; exit 1; }

# --- читаем свойства модуля ---
get_prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2-; }
MC="$(get_prop minecraft_version)"
YARN="$(get_prop yarn_mappings)"
LOADER="$(get_prop loader_version)"
FAPI="$(get_prop fabric_version)"
JAVA_VER="$(get_prop java_version)"
MODE="$(get_prop mappings_mode)"
VARIANT="$(get_prop code_variant)"
MIXIN_DIRS="$(get_prop mixin_dirs)"
MIXIN_RES="$(get_prop mixin_resources)"
MC_DEPENDS="$(get_prop mc_depends)"
LOADER_MIN="$(get_prop loader_min)"
MOD_VERSION="$(grep -E '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2-)"
VERSION="${MOD_VERSION}+${MC}"

: "${JAVA_HOME:?Укажите JAVA_HOME (JDK, совместимый с java_version=$JAVA_VER)}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
JAR="$JAVA_HOME/bin/jar"

WORK="${WORK:-$ROOT/.manual-build/$MODULE}"
mkdir -p "$WORK/libs" "$WORK/tools" "$WORK/named-mods" "$WORK/classes" "$WORK/staging" "$ROOT/versions/$MODULE/build/libs"
cd "$WORK"

echo "== MyOptimize Ultra: модуль $MODULE (MC $MC, режим $MODE, Java $JAVA_VER) =="

echo "== [1/6] Скачивание зависимостей =="
if [ "$MODE" = "mojmap" ]; then
  python3 - "$MC" "$LOADER" "$FAPI" <<'PYEOF'
import json, os, re, sys, urllib.request, concurrent.futures
mc, loader, fapi = sys.argv[1:4]
BASE = os.getcwd()
def get(url): return urllib.request.urlopen(url, timeout=60).read()
def dl(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0: return
    urllib.request.urlretrieve(url, path)
    print('  ok', os.path.basename(path), os.path.getsize(path))
m = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
v = [x for x in m['versions'] if x['id'] == mc][0]
vj = json.loads(get(v['url']))
dl(vj['downloads']['client']['url'], BASE + '/client.jar')
dl(vj['downloads']['server']['url'], BASE + '/server.jar')
urls = [lib['downloads']['artifact']['url'] for lib in vj['libraries'] if lib.get('downloads', {}).get('artifact')]
print('  библиотек:', len(urls))
with concurrent.futures.ThreadPoolExecutor(16) as ex:
    list(ex.map(lambda u: dl(u, BASE + '/libs/' + u.split('/')[-1]), urls))
dl(f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar', BASE + '/tools/fabric-loader.jar')
pom = get(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{fapi}/fabric-api-{fapi}.pom').decode()
WANT = ('fabric-api-base', 'fabric-lifecycle-events-v1', 'fabric-key-mapping-api-v1', 'fabric-resource-loader-v1')
for block in re.findall(r'<dependency>(.*?)</dependency>', pom, re.S):
    ma = re.search(r'<artifactId>([^<]+)</artifactId>', block)
    mv = re.search(r'<version>([^<]+)</version>', block)
    if ma and mv and ma.group(1) in WANT:
        a, ver = ma.group(1), mv.group(1)
        dl(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/{a}/{ver}/{a}-{ver}.jar', BASE + f'/tools/{a}.jar')
meta = get('https://maven.fabricmc.net/net/fabricmc/sponge-mixin/maven-metadata.xml').decode()
sm = re.findall(r'<version>([^<]+)</version>', meta)
mixin_ver = sorted(v for v in sm if v.startswith('0.15'))[-1]
dl(f'https://maven.fabricmc.net/net/fabricmc/sponge-mixin/{mixin_ver}/sponge-mixin-{mixin_ver}.jar', BASE + '/tools/sponge-mixin.jar')
asm_ver = '9.7.1'
for art in ['asm', 'asm-tree', 'asm-commons', 'asm-util', 'asm-analysis']:
    dl(f'https://repo1.maven.org/maven2/org/ow2/asm/{art}/{asm_ver}/{art}-{asm_ver}.jar', BASE + f'/tools/{art}.jar')
print('  зависимости готовы (mojmap)')
PYEOF
else
  python3 - "$MC" "$YARN" "$LOADER" "$FAPI" <<'PYEOF'
import json, os, re, sys, urllib.request, concurrent.futures, zipfile
mc, yarn_ver, loader, fapi = sys.argv[1:5]
BASE = os.getcwd()
def get(url): return urllib.request.urlopen(url, timeout=60).read()
def dl(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0: return
    urllib.request.urlretrieve(url, path)
    print('  ok', os.path.basename(path), os.path.getsize(path))
m = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
v = [x for x in m['versions'] if x['id'] == mc][0]
vj = json.loads(get(v['url']))
dl(vj['downloads']['client']['url'], BASE + '/client.jar')
dl(vj['downloads']['server']['url'], BASE + '/server.jar')
urls = [lib['downloads']['artifact']['url'] for lib in vj['libraries'] if lib.get('downloads', {}).get('artifact')]
print('  библиотек:', len(urls))
with concurrent.futures.ThreadPoolExecutor(16) as ex:
    list(ex.map(lambda u: dl(u, BASE + '/libs/' + u.split('/')[-1]), urls))
dl(f'https://maven.fabricmc.net/net/fabricmc/yarn/{yarn_ver}/yarn-{yarn_ver}-v2.jar', BASE + '/tools/yarn-v2.jar')
dl(f'https://maven.fabricmc.net/net/fabricmc/intermediary/{mc}/intermediary-{mc}.jar', BASE + '/tools/intermediary.jar')
dl(f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar', BASE + '/tools/fabric-loader.jar')
dl(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{fapi}/fabric-api-{fapi}.jar', BASE + '/tools/fabric-api.jar')
pom = get(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{fapi}/fabric-api-{fapi}.pom').decode()
WANT = ('fabric-api-base', 'fabric-lifecycle-events-v1', 'fabric-key-binding-api-v1', 'fabric-resource-loader-v0')
for block in re.findall(r'<dependency>(.*?)</dependency>', pom, re.S):
    ma = re.search(r'<artifactId>([^<]+)</artifactId>', block)
    mv = re.search(r'<version>([^<]+)</version>', block)
    if ma and mv and ma.group(1) in WANT:
        a, ver = ma.group(1), mv.group(1)
        dl(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/{a}/{ver}/{a}-{ver}.jar', BASE + f'/tools/{a}.jar')
meta = get('https://maven.fabricmc.net/net/fabricmc/sponge-mixin/maven-metadata.xml').decode()
sm = re.findall(r'<version>([^<]+)</version>', meta)
mixin_ver = sorted(v for v in sm if v.startswith('0.15'))[-1]
dl(f'https://maven.fabricmc.net/net/fabricmc/sponge-mixin/{mixin_ver}/sponge-mixin-{mixin_ver}.jar', BASE + '/tools/sponge-mixin.jar')
tr = re.findall(r'<version>([^<]+)</version>', get('https://maven.fabricmc.net/net/fabricmc/tiny-remapper/maven-metadata.xml').decode())
tr_ver = sorted(tr, key=lambda v: tuple(int(x) for x in re.findall(r'\d+', v)[:3]))[-1]
dl(f'https://maven.fabricmc.net/net/fabricmc/tiny-remapper/{tr_ver}/tiny-remapper-{tr_ver}-fat.jar', BASE + '/tools/tiny-remapper.jar')
asm_ver = '9.7.1'
for art in ['asm', 'asm-tree', 'asm-commons', 'asm-util', 'asm-analysis']:
    dl(f'https://repo1.maven.org/maven2/org/ow2/asm/{art}/{asm_ver}/{art}-{asm_ver}.jar', BASE + f'/tools/{art}.jar')
for name, out in [('yarn-v2.jar', 'yarn.tiny'), ('intermediary.jar', 'intermediary.tiny')]:
    with zipfile.ZipFile(BASE + '/tools/' + name) as z:
        member = [n for n in z.namelist() if n.endswith('mappings.tiny')][0]
        open(BASE + '/tools/' + out, 'wb').write(z.read(member))
print('  зависимости готовы (yarn)')
PYEOF
fi

if [ "$MODE" = "yarn" ]; then
  echo "== [2/6] Слияние client+server, ремап official -> intermediary -> named =="
  rm -rf merged mc-merged.jar mc-intermediary.jar mc-named.jar
  mkdir merged
  (cd merged && unzip -qo ../client.jar && unzip -qo ../server.jar)
  "$JAR" cf mc-merged.jar -C merged .
  "$JAVA" -Xmx1100m -jar tools/tiny-remapper.jar mc-merged.jar mc-intermediary.jar tools/intermediary.tiny official intermediary libs/*.jar 2>&1 | grep -E "ERROR|Finished" || true
  "$JAVA" -Xmx1100m -jar tools/tiny-remapper.jar mc-intermediary.jar mc-named.jar tools/yarn.tiny intermediary named libs/*.jar 2>&1 | grep -E "ERROR|Finished" || true

  echo "== [3/6] Ремап модулей Fabric API intermediary -> named =="
  for m in fabric-api-base fabric-lifecycle-events-v1 fabric-key-binding-api-v1 fabric-resource-loader-v0; do
    "$JAVA" -Xmx1100m -jar tools/tiny-remapper.jar tools/$m.jar named-mods/$m.jar tools/yarn.tiny intermediary named \
      mc-intermediary.jar libs/*.jar tools/fabric-loader.jar tools/sponge-mixin.jar \
      tools/fabric-api.jar tools/fabric-api-base.jar tools/fabric-lifecycle-events-v1.jar \
      tools/fabric-key-binding-api-v1.jar tools/fabric-resource-loader-v0.jar 2>&1 | grep -E "ERROR|Finished" || true
  done
  CP="mc-named.jar:$(ls libs/*.jar | tr '\n' ':')tools/fabric-loader.jar:tools/fabric-api.jar:named-mods/fabric-api-base.jar:named-mods/fabric-lifecycle-events-v1.jar:named-mods/fabric-key-binding-api-v1.jar:named-mods/fabric-resource-loader-v0.jar:tools/sponge-mixin.jar:tools/asm.jar:tools/asm-tree.jar:tools/asm-commons.jar:tools/asm-util.jar:tools/asm-analysis.jar"
else
  CP="client.jar:server.jar:$(ls libs/*.jar | tr '\n' ':')tools/fabric-loader.jar:tools/fabric-api-base.jar:tools/fabric-lifecycle-events-v1.jar:tools/fabric-key-mapping-api-v1.jar:tools/fabric-resource-loader-v1.jar:tools/sponge-mixin.jar:tools/asm.jar:tools/asm-tree.jar:tools/asm-commons.jar:tools/asm-util.jar:tools/asm-analysis.jar"
fi

echo "== [4/6] Компиляция =="
{
  find "$ROOT/src/main/java" -name '*.java'
  find "$ROOT/src/$VARIANT/java" -name '*.java'
  IFS=',' read -ra DIRS <<< "$MIXIN_DIRS"
  for d in "${DIRS[@]}"; do
    find "$ROOT/src/mixin/$d/java" -name '*.java'
  done
} > sources.txt
echo "  исходников: $(wc -l < sources.txt)"
rm -rf classes && mkdir classes
"$JAVAC" -J-Xmx700m -proc:none -encoding UTF-8 -nowarn --release "$JAVA_VER" -cp "$CP" -d classes @sources.txt
echo "  классов: $(find classes -name '*.class' | wc -l)"

echo "== [5/6] Staging-jar =="
rm -rf staging && mkdir staging
cp -r classes/* staging/
cp -r "$ROOT/src/main/resources/." staging/
sed -e "s/\${version}/$VERSION/" -e "s/\${mcDepends}/$MC_DEPENDS/" -e "s/\${javaVersion}/$JAVA_VER/" -e "s/\${loaderMin}/$LOADER_MIN/" \
  "$ROOT/src/main/resources/fabric.mod.json" > staging/fabric.mod.json
cp "$ROOT/src/mixin/$MIXIN_RES/resources/myoptimize-ultra.mixins.json" staging/
cp "$ROOT/LICENSE" "staging/LICENSE_myoptimize-ultra"
"$JAR" cf mod-staged.jar -C staging .

OUT="$ROOT/versions/$MODULE/build/libs/myoptimize-ultra-${VERSION}.jar"
if [ "$MODE" = "yarn" ]; then
  echo "== [6/6] Ремап named -> intermediary (MixinExtension) =="
  cat > RemapLauncher.java <<'JAVAEOF'
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RemapLauncher {
    public static void main(String[] args) throws Exception {
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        List<Path> classpath = new ArrayList<>();
        for (int i = 5; i < args.length; i++) classpath.add(Paths.get(args[i]));
        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createTinyMappingProvider(Paths.get(args[2]), args[3], args[4]))
            .extension(new MixinExtension())
            .ignoreConflicts(true)
            .threads(2)
            .build();
        remapper.readInputs(input);
        remapper.readClassPath(classpath.toArray(new Path[0]));
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            consumer.addNonClassFiles(input);
            remapper.apply(consumer);
        }
        remapper.finish();
        System.out.println("REMAP DONE: " + output);
    }
}
JAVAEOF
  mkdir -p launcher-classes
  "$JAVAC" -cp tools/tiny-remapper.jar -d launcher-classes RemapLauncher.java
  rm -f mod-final.jar
  "$JAVA" -Xmx1100m -cp tools/tiny-remapper.jar:launcher-classes RemapLauncher \
    mod-staged.jar mod-final.jar tools/yarn.tiny named intermediary \
    mc-named.jar libs/*.jar tools/fabric-loader.jar \
    named-mods/fabric-api-base.jar named-mods/fabric-lifecycle-events-v1.jar \
    named-mods/fabric-key-binding-api-v1.jar named-mods/fabric-resource-loader-v0.jar \
    tools/sponge-mixin.jar
  cp mod-final.jar "$OUT"
else
  echo "== [6/6] Упаковка (mojmap — без ремапа) =="
  cp mod-staged.jar "$OUT"
fi

"$JAR" cf "$ROOT/versions/$MODULE/build/libs/myoptimize-ultra-${VERSION}-sources.jar" -C "$ROOT/src/main/java" . 2>/dev/null || true

echo
echo "ГОТОВО: $OUT"
ls -la "$ROOT/versions/$MODULE/build/libs/"
echo "Временная сборка: $WORK (можно удалить)"
