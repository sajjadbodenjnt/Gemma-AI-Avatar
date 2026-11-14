import argparse
import sys
import bpy

parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True)
parser.add_argument("--output", required=True)
parser.add_argument("--texture", required=True)
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.fbx(filepath=args.input)
fallback_texture = bpy.data.images.load(args.texture, check_existing=True)
for material in bpy.data.materials:
    if not material.use_nodes or material.node_tree is None:
        continue
    for node in material.node_tree.nodes:
        if node.type == "TEX_IMAGE" and node.image is not None and not node.image.has_data:
            node.image = fallback_texture
for image in bpy.data.images:
    try:
        if image.has_data:
            image.pack()
    except Exception:
        pass
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(
    filepath=args.output, export_format="GLB", export_animations=True,
    export_skins=True, export_morph=True, export_materials="EXPORT",
    export_apply=False, export_yup=True,
)
print("Exported offline Sophia GLB:", args.output)
