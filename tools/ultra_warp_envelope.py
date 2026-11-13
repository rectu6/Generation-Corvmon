# Room a Pokémon model takes in the Ultra Warp Ride tunnel, for the configs:
# - centre (client ultra_warp_ride_client.json, rider.<sun|moon>.centerX/Y/Z): middle
#   of the model's bounds, the point the player steers;
# - halfWidth, halfHeight, halfLength (server, riders.<sun|moon>): the gameplay shape,
#   an ellipse across the tunnel and its length along it;
# - hitRadius (server ultra_warp_ride.json, riders.<sun|moon>.hitRadius): radius of the
#   disc around that centre which holds the model across the tunnel (width x height),
#   plus what its length adds when it tilts by up to --tilt degrees (nose up/down,
#   turning): a disc stays the same when the Pokémon banks or beats its wings.
# Bounds of the model at rest (GLB positions) times its config.json scale, in blocks.
#
# Usage: python tools/ultra_warp_envelope.py <model.pk | extracted folder> [--jar <mod jar>] [--tilt 15]
# A .pk is a 7z archive: it is opened with the 7z reader shaded in the mod's jar
# (needs a JDK 21 "java" on the PATH, or JAVA_HOME).
import json, math, os, struct, subprocess, sys, tempfile

EXTRACT = r'''
import gg.generations.rarecandy.shaded.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import gg.generations.rarecandy.shaded.commons.compress.archivers.sevenz.SevenZFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtractPk {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[1]);
        try (SevenZFile file = SevenZFile.builder().setFile(new File(args[0])).get()) {
            SevenZArchiveEntry entry;
            while ((entry = file.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !(name.equals("model.glb") || name.equals("config.json"))) continue;
                byte[] data = new byte[(int) entry.getSize()];
                int read = 0;
                while (read < data.length) {
                    int n = file.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                Files.write(out.resolve(name), data);
            }
        }
    }
}
'''


def extract(pk, jar):
    folder = tempfile.mkdtemp(prefix="ultra_warp_")
    source = os.path.join(folder, "ExtractPk.java")
    open(source, "w", encoding="utf-8").write(EXTRACT)
    java = os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else "java"
    subprocess.run([java, "-cp", jar, source, pk, folder], check=True)
    return folder


def bounds(folder):
    config = json.load(open(os.path.join(folder, "config.json"), encoding="utf-8"))
    scale = config.get("scale") or 1.0
    data = open(os.path.join(folder, "model.glb"), "rb").read()
    length = struct.unpack("<I", data[12:16])[0]
    gltf = json.loads(data[20:20 + length])
    lo = [math.inf] * 3
    hi = [-math.inf] * 3
    for mesh in gltf.get("meshes", []):
        for primitive in mesh["primitives"]:
            accessor = gltf["accessors"][primitive["attributes"]["POSITION"]]
            for i in range(3):
                lo[i] = min(lo[i], accessor["min"][i])
                hi[i] = max(hi[i], accessor["max"][i])
    return [v * scale for v in lo], [v * scale for v in hi], scale


def main():
    args = sys.argv[1:]
    if not args:
        print("usage: python tools/ultra_warp_envelope.py <model.pk | folder> [--jar <mod jar>] [--tilt 15]")
        sys.exit(1)
    target = args[0]
    jar = args[args.index("--jar") + 1] if "--jar" in args else None
    tilt = float(args[args.index("--tilt") + 1]) if "--tilt" in args else 15.0
    folder = target
    if target.endswith(".pk"):
        if not jar:
            sys.exit("A .pk needs --jar <mod jar> (its 7z reader)")
        folder = extract(target, jar)
    lo, hi, scale = bounds(folder)
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    half = [(hi[i] - lo[i]) / 2 for i in range(3)]
    across = math.hypot(half[0], half[1])
    hit = across + half[2] * math.sin(math.radians(tilt))
    print(f"{os.path.basename(target)}: scale {scale}")
    print(f"  bounds  x {lo[0]:.2f} .. {hi[0]:.2f}   y {lo[1]:.2f} .. {hi[1]:.2f}   z {lo[2]:.2f} .. {hi[2]:.2f}")
    print(f"  size    width {2 * half[0]:.2f}   height {2 * half[1]:.2f}   length {2 * half[2]:.2f}")
    print(f"  client  centerX {centre[0]:.2f}  centerY {centre[1]:.2f}  centerZ {centre[2]:.2f}")
    print(f"  server  hitRadius {math.ceil(hit * 10) / 10:.1f}   (across {across:.2f} + length at {tilt:g} deg)")
    print(f"  server  halfWidth {half[0]:.2f}  halfHeight {half[1]:.2f}  halfLength {half[2]:.2f}")


main()
