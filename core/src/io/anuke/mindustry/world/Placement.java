package io.anuke.mindustry.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.game.SpawnPoint;
import io.anuke.mindustry.graphics.Fx;
import io.anuke.mindustry.resource.ItemStack;
import io.anuke.mindustry.resource.Recipe;
import io.anuke.mindustry.resource.Recipes;
import io.anuke.mindustry.world.blocks.Blocks;
import io.anuke.mindustry.world.blocks.ProductionBlocks;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.entities.Entities;
import io.anuke.ucore.entities.SolidEntity;

import static io.anuke.mindustry.Vars.*;

public class Placement {
    private static final Rectangle rect = new Rectangle();
    private static Array<Tile> tempTiles = new Array<>();

    /**Returns block type that was broken, or null if unsuccesful.*/
    public static Block breakBlock(int x, int y, boolean effect, boolean sound){
        Tile tile = world[player.dimension].tile(x, y);

        if(tile == null) return null;

        Block block = tile.isLinked() ? tile.getLinked().block() : tile.block();
        block.onBreak(tile);
        Recipe result = Recipes.getByResult(block);

        if(result != null){
            for(ItemStack stack : result.requirements){
                state.inventory.addItem(stack.item, (int)(stack.amount * breakDropAmount));
            }
        }

        if(tile.block().drops != null){
            state.inventory.addItem(tile.block().drops.item, tile.block().drops.amount);
        }

        if(sound) threads.run(() -> Effects.sound("break", x * tilesize, y * tilesize));

        if(!tile.block().isMultiblock() && !tile.isLinked()){
            tile.setBlock(Blocks.air);
            if(effect) Effects.effect(Fx.breakBlock, tile.worldx(), tile.worldy(), tile.dimension);
        }else{
            Tile target = tile.isLinked() ? tile.getLinked() : tile;
            Array<Tile> removals = target.getLinkedTiles(tempTiles);
            for(Tile toremove : removals){
                //note that setting a new block automatically unlinks it
                toremove.setBlock(Blocks.air);
                if(effect) Effects.effect(Fx.breakBlock, toremove.worldx(), toremove.worldy(), toremove.dimension);
            }
        }

        return block;
    }

    public static void placeBlock(int x, int y, Block result, int rotation, boolean effects, boolean sound) {
        Tile tile = world[0].tile(x, y);

        //just in case
        if (tile == null) return;

        tile.setBlock(result, rotation);

        if (result.isMultiblock()) {
            int offsetx = -(result.size - 1) / 2;
            int offsety = -(result.size - 1) / 2;

            for (int dx = 0; dx < result.size; dx++) {
                for (int dy = 0; dy < result.size; dy++) {
                    int worldx = dx + offsetx + x;
                    int worldy = dy + offsety + y;
                    if (!(worldx == x && worldy == y)) {
                        Tile toplace = world[tile.dimension].tile(worldx, worldy);
                        if (toplace != null)
                            toplace.setLinked((byte) (dx + offsetx), (byte) (dy + offsety));
                    }

                    if (effects) Effects.effect(Fx.place, worldx * tilesize, worldy * tilesize, tile.dimension);
                }
            }
        } else if (effects) Effects.effect(Fx.place, x * tilesize, y * tilesize, tile.dimension);

        if (effects && sound) threads.run(() -> Effects.sound("place", x * tilesize, y * tilesize));
    }

    public static boolean validPlace(int x, int y, int dimension, Block type){
        for(int i = 0; i < world[player.dimension].getSpawns().size; i ++){
            SpawnPoint spawn = world[player.dimension].getSpawns().get(i);
            if(Vector2.dst(x * tilesize, y * tilesize, spawn.start.worldx(), spawn.start.worldy()) < enemyspawnspace){
                return false;
            }
        }

        Recipe recipe = Recipes.getByResult(type);

        if(recipe == null || !state.inventory.hasItems(recipe.requirements)){
            return false;
        }

        if(!global.getResearchStatus(Recipes.getResearchByResult(recipe)))
                return false;

        rect.setSize(type.size * tilesize, type.size * tilesize);
        Vector2 offset = type.getPlaceOffset();
        rect.setCenter(offset.x + x * tilesize, offset.y + y * tilesize);

        synchronized (Entities.entityLock) {
            for (SolidEntity e : world[dimension].ents.getNearby(world[dimension].enemyGroup, x * tilesize, y * tilesize, tilesize * 2f)) {
                if (e == null) continue; //not sure why this happens?
                Rectangle rect = e.hitbox.getRect(e.x, e.y);

                if (Placement.rect.overlaps(rect)) {
                    return false;
                }
            }
        }

        if(type.solid || type.solidifes) {
            for (Player player : world[dimension].playerGroup.all()) {
                if (!player.isAndroid && rect.overlaps(player.hitbox.getRect(player.x, player.y))) {
                    return false;
                }
            }
        }

        Tile tile = world[player.dimension].tile(x, y);

        if(tile == null || (isSpawnPoint(tile) && (type.solidifes || type.solid))) return false;

        if(type.isMultiblock()){
            int offsetd = -(type.size-1)/2;

            for(int d = 0; d < type.size; d++) {
                Tile other = world[player.dimension].tile(x + d + offsetd, y + d + offsetd);
                if (other == null || (other.block() != Blocks.air && !other.block().alwaysReplace) || isSpawnPoint(other)) {
                    return false;
                }
            }
            return true;
        }else {
            return tile.block() != type
                    && (type.canReplace(tile.block()) || tile.block().alwaysReplace)
                    && tile.block().isMultiblock() == type.isMultiblock() || tile.block() == Blocks.air;
        }
    }

    public static boolean isSpawnPoint(Tile tile){
        return tile != null && tile.x == world[player.dimension].getCore().x && tile.y == world[player.dimension].getCore().y - 2;
    }

    public static boolean validBreak(int x, int y){
        Tile tile = world[player.dimension].tile(x, y);

        if(tile == null || tile.block() == ProductionBlocks.core) return false;

        if(tile.isLinked() && tile.getLinked().block() == ProductionBlocks.core){
            return false;
        }

        return tile.breakable();
    }
}
