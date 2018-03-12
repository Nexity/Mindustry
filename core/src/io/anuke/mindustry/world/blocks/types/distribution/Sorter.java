package io.anuke.mindustry.world.blocks.types.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.style.TextureRegionDrawable;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Sorter extends Block{

	
	public Sorter(String name) {
		super(name);
		update = true;
		solid = true;
		instantTransfer = true;
	}
	
	@Override
	public void draw(Tile tile){
		super.draw(tile);
		
		SorterEntity entity = tile.entity();
		
		TextureRegion region = entity.sortItem.region;
		Tmp.tr1.setRegion(region, 4, 4, 1, 1);
		
		Draw.rect(Tmp.tr1, tile.worldx(), tile.worldy(), 4f, 4f);
	}

	@Override
	public boolean canReplace(Block other){
		return other instanceof Conveyor || other instanceof Router;
	}
	
	@Override
	public boolean acceptItem(Item item, Tile tile, Tile source){
		Tile to = getTileTarget(item, tile, source, false);
		
		return to != null && to.block().acceptItem(item, to, tile);
	}
	
	@Override
	public void handleItem(Item item, Tile tile, Tile source){
		Tile to = getTileTarget(item, tile, source, true);

		to.block().handleItem(item, to, tile);
	}
	
	Tile getTileTarget(Item item, Tile dest, Tile source, boolean flip){
		SorterEntity entity = dest.entity();
		
		int dir = source.relativeTo(dest.x, dest.y);
		if(dir == -1) return null;
		Tile to;
		
		if((item == entity.sortItem && !entity.inverted)
			||(item != entity.sortItem && entity.inverted))
			to = dest.getNearby(dir);
		else
		{
			Tile a = dest.getNearby(Mathf.mod(dir - 1, 4));
			Tile b = dest.getNearby(Mathf.mod(dir + 1, 4));
			boolean ac = a != null && !(a.block().instantTransfer && source.block().instantTransfer) &&
								a.block().acceptItem(item, a, dest);
			boolean bc = b != null && !(b.block().instantTransfer && source.block().instantTransfer) &&
								b.block().acceptItem(item, b, dest);
			
			if(ac && !bc){
				to = a;
			}else if(bc && !ac){
				to = b;
			}else if(!bc){
				return null;
			}else{
				if(dest.getDump() == 0){
					to = a;
					if(flip)
						dest.setDump((byte)1);
				}else{
					to = b;
					if(flip)
						dest.setDump((byte)0);
				}
			}
		}
		
		return to;
	}

	
	public void invertConfigure(Tile tile, byte data, boolean invert) {
		SorterEntity entity = tile.entity();
		if(entity != null){
			entity.sortItem = Item.getByID(data);
            entity.inverted = invert;
		}
	}

	@Override
	public boolean isConfigurable(Tile tile){
		return true;
	}
	
	@Override
	public void buildTable(Tile tile, Table table){
		SorterEntity entity = tile.entity();

		Array<Item> items = Item.getAllItems();

		ButtonGroup<ImageButton> group = new ButtonGroup<>();
		Table cont = new Table();
		cont.margin(4);
		cont.marginBottom(5);

		cont.add().colspan(4).height(105f);
		cont.row();

		for(int i = 0; i < items.size; i ++){
			final int f = i;
			ImageButton button = cont.addImageButton("white", "toggle", 24, () -> {
				entity.sortItem = items.get(f);
				setInvertConfigure(tile, (byte)f, entity.inverted);
			}).size(38, 42).padBottom(-5.1f).group(group).get();
			button.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(items.get(i).region));
			button.setChecked(entity.sortItem.id == f);

			if(i%4 == 3){
				cont.row();
			}
		}

		cont.row();

		TextButton invBut = cont.addButton("Invert","toggle", () -> {
			entity.inverted = entity.inverted ? false : true;
            setInvertConfigure(tile, (byte)entity.sortItem.id, entity.inverted);
		}).padBottom(-5.1f).get();
		invBut.setChecked(entity.inverted);


		table.add(cont);
	}
	
	@Override
	public TileEntity getEntity(){
		return new SorterEntity();
	}

	public static class SorterEntity extends TileEntity{
		public Item sortItem = Item.iron;
		public boolean inverted = false;

		@Override
		public void write(DataOutputStream stream) throws IOException{
			stream.writeByte(sortItem.id);
			stream.writeByte((byte)(inverted ? 1 : 0));
		}
		
		@Override
		public void read(DataInputStream stream) throws IOException{
			sortItem = Item.getAllItems().get(stream.readByte());
			inverted = stream.readByte() == 1;
		}
	}
}
