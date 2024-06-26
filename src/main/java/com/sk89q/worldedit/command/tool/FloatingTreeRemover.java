/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command.tool;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.PlayerDirection;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;

/**
 * A pickaxe mode that removes floating treetops (logs and leaves not connected
 * to anything else)
 */
public class FloatingTreeRemover implements BlockTool {

    private static final BaseBlock AIR = new BaseBlock(BlockID.AIR);
    private int rangeSq;

    public FloatingTreeRemover() {
        rangeSq = 100 * 100;
    }

    @Override
    public boolean canUse(Actor player) {
        return player.hasPermission("worldedit.tool.deltree");
    }

    @Override
    public boolean actPrimary(Platform server, LocalConfiguration config, Player player, LocalSession session,
        Location clicked) {

        final World world = (World) clicked.getExtent();

        switch (world.getBlockType(clicked.toVector())) {
            case BlockID.LOG:
            case BlockID.LOG2:
            case BlockID.LEAVES:
            case BlockID.LEAVES2:
            case BlockID.BROWN_MUSHROOM_CAP:
            case BlockID.RED_MUSHROOM_CAP:
            case BlockID.VINE:
                break;

            default:
                player.printError("That's not a tree.");
                return true;
        }

        final EditSession editSession = session.createEditSession(player);

        try {
            final Set<Vector> blockSet = bfs(world, clicked.toVector());
            if (blockSet == null) {
                player.printError("That's not a floating tree.");
                return true;
            }

            for (Vector blockVector : blockSet) {
                final int typeId = editSession.getBlock(blockVector)
                    .getType();
                switch (typeId) {
                    case BlockID.LOG:
                    case BlockID.LOG2:
                    case BlockID.LEAVES:
                    case BlockID.LEAVES2:
                    case BlockID.BROWN_MUSHROOM_CAP:
                    case BlockID.RED_MUSHROOM_CAP:
                    case BlockID.VINE:
                        editSession.setBlock(blockVector, AIR);
                }
            }
        } catch (MaxChangedBlocksException e) {
            player.printError("Max blocks change limit reached.");
        } finally {
            session.remember(editSession);
        }

        return true;
    }

    Vector[] recurseDirections = { PlayerDirection.NORTH.vector(), PlayerDirection.EAST.vector(),
        PlayerDirection.SOUTH.vector(), PlayerDirection.WEST.vector(), PlayerDirection.UP.vector(),
        PlayerDirection.DOWN.vector(), };

    /**
     * Helper method.
     *
     * @param world  the world that contains the tree
     * @param origin any point contained in the floating tree
     * @return a set containing all blocks in the tree/shroom or null if this is not a floating tree/shroom.
     */
    private Set<Vector> bfs(World world, Vector origin) throws MaxChangedBlocksException {
        final Set<Vector> visited = new HashSet<Vector>();
        final LinkedList<Vector> queue = new LinkedList<Vector>();

        queue.addLast(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            final Vector current = queue.removeFirst();
            for (Vector recurseDirection : recurseDirections) {
                final Vector next = current.add(recurseDirection);
                if (origin.distanceSq(next) > rangeSq) {
                    // Maximum range exceeded => stop walking
                    continue;
                }

                if (visited.add(next)) {
                    switch (world.getBlockType(next)) {
                        case BlockID.AIR:
                        case BlockID.SNOW:
                            // we hit air or snow => stop walking this route
                            continue;

                        case BlockID.LOG:
                        case BlockID.LOG2:
                        case BlockID.LEAVES:
                        case BlockID.LEAVES2:
                        case BlockID.BROWN_MUSHROOM_CAP:
                        case BlockID.RED_MUSHROOM_CAP:
                        case BlockID.VINE:
                            // queue next point
                            queue.addLast(next);
                            break;

                        default:
                            // we hit something solid - evaluate where we came from
                            final int curId = world.getBlockType(current);
                            if (curId == BlockID.LEAVES || curId == BlockID.LEAVES2 || curId == BlockID.VINE) {
                                // leaves touching a wall/the ground => stop walking this route
                                continue;
                            } else {
                                // log/shroom touching a wall/the ground => this is not a floating tree, bail out
                                return null;
                            }
                    } // switch
                } // if
            } // for
        } // while

        return visited;
    } // bfs
}
