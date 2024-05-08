package com.sk89q.worldedit.extent.clipboard.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.*;

import net.minecraft.block.Block;

import com.sk89q.jnbt.*;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.registry.WorldData;

public class SchematicPlusWriter implements ClipboardWriter {

    private final NBTOutputStream outputStream;

    /**
     * Create a new schematicPlus writer. Slower and larger than schematic. Only used on cross-save-export.
     *
     * @param outputStream the output stream to write to
     */
    public SchematicPlusWriter(NBTOutputStream outputStream) {
        checkNotNull(outputStream);
        this.outputStream = outputStream;
    }

    @Override
    public void write(Clipboard clipboard, WorldData data) throws IOException {
        Region region = clipboard.getRegion();
        Vector origin = clipboard.getOrigin();
        Vector min = region.getMinimumPoint();
        Vector offset = min.subtract(origin);
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();

        // ====================================================================
        // Metadata
        // ====================================================================

        HashMap<String, Tag> schematicPlus = new HashMap<>();
        schematicPlus.put("Width", new IntTag(width));
        schematicPlus.put("Length", new IntTag(length));
        schematicPlus.put("Height", new IntTag(height));
        schematicPlus.put("Materials", new StringTag("Alpha"));
        schematicPlus.put("WEOriginX", new IntTag(min.getBlockX()));
        schematicPlus.put("WEOriginY", new IntTag(min.getBlockY()));
        schematicPlus.put("WEOriginZ", new IntTag(min.getBlockZ()));
        schematicPlus.put("WEOffsetX", new IntTag(offset.getBlockX()));
        schematicPlus.put("WEOffsetY", new IntTag(offset.getBlockY()));
        schematicPlus.put("WEOffsetZ", new IntTag(offset.getBlockZ()));

        // ====================================================================
        // Block handling
        // ====================================================================

        List<Tag> blocks = new ArrayList<>();
        List<Tag> tileEntities = new ArrayList<>();

        for (Vector point : region) {
            Vector relative = point.subtract(min);
            int x = relative.getBlockX();
            int y = relative.getBlockY();
            int z = relative.getBlockZ();

            BaseBlock block = clipboard.getBlock(point);

            Map<String, Tag> blockValues = new HashMap<>();
            blockValues.put(
                "id",
                new StringTag(
                    Block.getBlockById(block.getId())
                        .getUnlocalizedName()));
            blockValues.put("damage", new IntTag(block.getData()));
            blockValues.put("x", new IntTag(x));
            blockValues.put("y", new IntTag(y));
            blockValues.put("z", new IntTag(z));
            blocks.add(new CompoundTag(blockValues));

            // Store TileEntity data
            CompoundTag rawTag = block.getNbtData();
            if (rawTag != null) {
                Map<String, Tag> values = new HashMap<String, Tag>();
                for (Map.Entry<String, Tag> entry : rawTag.getValue()
                    .entrySet()) {
                    values.put(entry.getKey(), entry.getValue());
                }

                values.put("id", new StringTag(block.getNbtId()));
                values.put("x", new IntTag(x));
                values.put("y", new IntTag(y));
                values.put("z", new IntTag(z));

                CompoundTag tileEntityTag = new CompoundTag(values);
                tileEntities.add(tileEntityTag);
            }
        }

        schematicPlus.put("Blocks", new ListTag(CompoundTag.class, blocks));
        schematicPlus.put("TileEntities", new ListTag(CompoundTag.class, tileEntities));

        // ====================================================================
        // Entities
        // ====================================================================

        List<Tag> entities = new ArrayList<>();
        for (Entity entity : clipboard.getEntities()) {
            BaseEntity state = entity.getState();

            if (state != null) {
                Map<String, Tag> values = new HashMap<>();

                // Put NBT provided data
                CompoundTag rawTag = state.getNbtData();
                if (rawTag != null) {
                    values.putAll(rawTag.getValue());
                }

                // Store our location data, overwriting any
                values.put("id", new StringTag(state.getTypeId()));
                values.put(
                    "Pos",
                    writeVector(
                        entity.getLocation()
                            .toVector()));
                values.put("Rotation", writeRotation(entity.getLocation()));

                CompoundTag entityTag = new CompoundTag(values);
                entities.add(entityTag);
            }
        }

        schematicPlus.put("Entities", new ListTag(CompoundTag.class, entities));

        // ====================================================================
        // Output
        // ====================================================================

        CompoundTag schematicPlusTag = new CompoundTag(schematicPlus);
        outputStream.writeNamedTag("SchematicPlus", schematicPlusTag);
    }

    private Tag writeVector(Vector vector) {
        List<DoubleTag> list = new ArrayList<DoubleTag>();
        list.add(new DoubleTag(vector.getX()));
        list.add(new DoubleTag(vector.getY()));
        list.add(new DoubleTag(vector.getZ()));
        return new ListTag(DoubleTag.class, list);
    }

    private Tag writeRotation(Location location) {
        List<FloatTag> list = new ArrayList<FloatTag>();
        list.add(new FloatTag(location.getYaw()));
        list.add(new FloatTag(location.getPitch()));
        return new ListTag(FloatTag.class, list);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
