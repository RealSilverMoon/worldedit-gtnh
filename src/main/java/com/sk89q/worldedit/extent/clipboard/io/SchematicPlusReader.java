package com.sk89q.worldedit.extent.clipboard.io;

import com.sk89q.jnbt.*;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.registry.BundledBlockData;
import com.sk89q.worldedit.world.registry.WorldData;
import com.sk89q.worldedit.world.storage.NBTConversions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class SchematicPlusReader implements ClipboardReader  {
    private static final Logger log = Logger.getLogger(SchematicPlusReader.class.getCanonicalName());
    private final NBTInputStream inputStream;

    /**
     * Create a new instance.
     *
     * @param inputStream the input stream to read from
     */
    public SchematicPlusReader(NBTInputStream inputStream) {
        checkNotNull(inputStream);
        this.inputStream = inputStream;
    }

    @Override
    public Clipboard read(WorldData data) throws IOException {
        // SchematicPlus tag
        NamedTag rootTag = inputStream.readNamedTag();
        if (!rootTag.getName()
                .equals("SchematicPlus")) {
            throw new IOException("Tag 'SchematicPlus' does not exist or is not first");
        }
        CompoundTag schematicPlusTag = (CompoundTag) rootTag.getTag();

        // Check
        Map<String, Tag> schematicPlus = schematicPlusTag.getValue();
        if (!schematicPlus.containsKey("Blocks")) {
            throw new IOException("SchematicPlus file is missing a 'Blocks' tag");
        }

        // Check type of Schematic
        String materials = requireTag(schematicPlus, "Materials", StringTag.class).getValue();
        if (!materials.equals("Alpha")) {
            throw new IOException("SchematicPlus file is not an Alpha schematicPlus");
        }

        // ====================================================================
        // Metadata
        // ====================================================================

        Vector origin;
        Region region;

        // Get information
        int width = requireTag(schematicPlus, "Width", IntTag.class).getValue();
        int height = requireTag(schematicPlus, "Height", IntTag.class).getValue();
        int length = requireTag(schematicPlus, "Length", IntTag.class).getValue();

        try {
            int originX = requireTag(schematicPlus, "WEOriginX", IntTag.class).getValue();
            int originY = requireTag(schematicPlus, "WEOriginY", IntTag.class).getValue();
            int originZ = requireTag(schematicPlus, "WEOriginZ", IntTag.class).getValue();
            Vector min = new Vector(originX, originY, originZ);

            int offsetX = requireTag(schematicPlus, "WEOffsetX", IntTag.class).getValue();
            int offsetY = requireTag(schematicPlus, "WEOffsetY", IntTag.class).getValue();
            int offsetZ = requireTag(schematicPlus, "WEOffsetZ", IntTag.class).getValue();
            Vector offset = new Vector(offsetX, offsetY, offsetZ);

            origin = min.subtract(offset);
            region = new CuboidRegion(
                    min,
                    min.add(width, height, length)
                            .subtract(Vector.ONE));
        } catch (IOException ignored) {
            origin = new Vector(0, 0, 0);
            region = new CuboidRegion(
                    origin,
                    origin.add(width, height, length)
                            .subtract(Vector.ONE));
        }

        // ====================================================================
        // Blocks
        // ====================================================================

        // Get blocks
        List<Tag> blocks = requireTag(schematicPlus, "Blocks", ListTag.class).getValue();
        Map<BlockVector, Map<String, Tag>> blockMap = new HashMap<>();
        for(Tag tag: blocks){
            if (!(tag instanceof CompoundTag b)) continue;
            int x = 0;
            int y = 0;
            int z = 0;
            Map<String, Tag> values = new HashMap<>();
            for (Map.Entry<String, Tag> entry : b.getValue()
                    .entrySet()) {
                switch (entry.getKey()) {
                    case "x":
                        if (entry.getValue() instanceof IntTag) {
                            x = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                    case "y":
                        if (entry.getValue() instanceof IntTag) {
                            y = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                    case "z":
                        if (entry.getValue() instanceof IntTag) {
                            z = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                }
                values.put(entry.getKey(), entry.getValue());
            }
            BlockVector vec = new BlockVector(x, y, z);
            blockMap.put(vec, values);
        }

        // Need to pull out tile entities
        List<Tag> tileEntities = requireTag(schematicPlus, "TileEntities", ListTag.class).getValue();
        Map<BlockVector, Map<String, Tag>> tileEntitiesMap = new HashMap<>();

        for (Tag tag : tileEntities) {
            if (!(tag instanceof CompoundTag t)) continue;

            int x = 0;
            int y = 0;
            int z = 0;

            Map<String, Tag> values = new HashMap<>();

            for (Map.Entry<String, Tag> entry : t.getValue()
                    .entrySet()) {
                switch (entry.getKey()) {
                    case "x":
                        if (entry.getValue() instanceof IntTag) {
                            x = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                    case "y":
                        if (entry.getValue() instanceof IntTag) {
                            y = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                    case "z":
                        if (entry.getValue() instanceof IntTag) {
                            z = ((IntTag) entry.getValue()).getValue();
                        }
                        break;
                }

                values.put(entry.getKey(), entry.getValue());
            }

            BlockVector vec = new BlockVector(x, y, z);
            tileEntitiesMap.put(vec, values);
        }

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(origin);

        // Don't log a torrent of errors
        int failedBlockSets = 0;

        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    BlockVector pt = new BlockVector(x, y, z);
                    String id= (String) blockMap.get(pt).get("id").getValue();
                    int damage=(int) blockMap.get(pt).get("damage").getValue();
                    BaseBlock block= new BaseBlock(Optional.ofNullable(BundledBlockData.getInstance().toLegacyId(id)).orElse(0)
                            ,damage);
                    if (tileEntitiesMap.containsKey(pt)) {
                        block.setNbtData(new CompoundTag(tileEntitiesMap.get(pt)));
                    }

                    try {
                        clipboard.setBlock(
                                region.getMinimumPoint()
                                        .add(pt),
                                block);
                    } catch (WorldEditException e) {
                        switch (failedBlockSets) {
                            case 0 -> log.log(Level.WARNING, "Failed to set block on a Clipboard", e);
                            case 1 -> log.log(
                                    Level.WARNING,
                                    "Failed to set block on a Clipboard (again) -- no more messages will be logged",
                                    e);
                            default -> {
                            }
                        }

                        failedBlockSets++;
                    }
                }
            }
        }

        // ====================================================================
        // Entities
        // ====================================================================

        try {
            List<Tag> entityTags = requireTag(schematicPlus, "Entities", ListTag.class).getValue();

            for (Tag tag : entityTags) {
                if (tag instanceof CompoundTag compound) {
                    String id = compound.getString("id");
                    Location location = NBTConversions
                            .toLocation(clipboard, compound.getListTag("Pos"), compound.getListTag("Rotation"));

                    if (!id.isEmpty()) {
                        BaseEntity state = new BaseEntity(id, compound);
                        clipboard.createEntity(location, state);
                    }
                }
            }
        } catch (IOException ignored) { // No entities? No problem
        }

        return clipboard;
    }

    private static <T extends Tag> T requireTag(Map<String, Tag> items, String key, Class<T> expected)
            throws IOException {
        if (!items.containsKey(key)) {
            throw new IOException("Schematic file is missing a \"" + key + "\" tag");
        }

        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new IOException(key + " tag is not of tag type " + expected.getName());
        }

        return expected.cast(tag);
    }

    @Nullable
    private static <T extends Tag> T getTag(CompoundTag tag, Class<T> expected, String key) {
        Map<String, Tag> items = tag.getValue();

        if (!items.containsKey(key)) {
            return null;
        }

        Tag test = items.get(key);
        if (!expected.isInstance(test)) {
            return null;
        }

        return expected.cast(test);
    }

}

