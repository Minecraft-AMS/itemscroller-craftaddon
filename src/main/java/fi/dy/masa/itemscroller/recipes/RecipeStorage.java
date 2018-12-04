package fi.dy.masa.itemscroller.recipes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.annotation.Nonnull;
import fi.dy.masa.itemscroller.ItemScroller;
import fi.dy.masa.itemscroller.Reference;
import fi.dy.masa.itemscroller.util.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.integrated.IntegratedServer;

public class RecipeStorage
{
    private final CraftingRecipe[] recipes;
    private final int recipeCount;
    private final boolean global;
    private int selected;
    private boolean dirty;

    public RecipeStorage(int recipeCount, boolean global)
    {
        this.recipes = new CraftingRecipe[recipeCount];
        this.recipeCount = recipeCount;
        this.global = global;
        this.initRecipes();
    }

    private void initRecipes()
    {
        for (int i = 0; i < this.recipes.length; i++)
        {
            this.recipes[i] = new CraftingRecipe();
        }
    }

    public int getSelection()
    {
        return this.selected;
    }

    public void changeSelectedRecipe(int index)
    {
        if (index >= 0 && index < this.recipes.length)
        {
            this.selected = index;
            this.dirty = true;
        }
    }

    public void scrollSelection(boolean forward)
    {
        this.changeSelectedRecipe(this.selected + (forward ? 1 : -1));
    }

    public int getRecipeCount()
    {
        return this.recipeCount;
    }

    /**
     * Returns the recipe for the given index.
     * If the index is invalid, then the first recipe is returned, instead of null.
     */
    @Nonnull
    public CraftingRecipe getRecipe(int index)
    {
        if (index >= 0 && index < this.recipes.length)
        {
            return this.recipes[index];
        }

        return this.recipes[0];
    }

    @Nonnull
    public CraftingRecipe getSelectedRecipe()
    {
        return this.getRecipe(this.getSelection());
    }

    public void storeCraftingRecipeToCurrentSelection(Slot slot, GuiContainer gui, boolean clearIfEmpty)
    {
        this.storeCraftingRecipe(this.getSelection(), slot, gui, clearIfEmpty);
    }

    public void storeCraftingRecipe(int index, Slot slot, GuiContainer gui, boolean clearIfEmpty)
    {
        this.getRecipe(index).storeCraftingRecipe(slot, gui, clearIfEmpty);
        this.dirty = true;
    }

    public void clearRecipe(int index)
    {
        this.getRecipe(index).clearRecipe();
        this.dirty = true;
    }

    private void readFromNBT(NBTTagCompound nbt)
    {
        if (nbt == null || nbt.contains("Recipes", Constants.NBT.TAG_LIST) == false)
        {
            return;
        }

        for (int i = 0; i < this.recipes.length; i++)
        {
            this.recipes[i].clearRecipe();
        }

        NBTTagList tagList = nbt.getList("Recipes", Constants.NBT.TAG_COMPOUND);
        int count = tagList.size();

        for (int i = 0; i < count; i++)
        {
            NBTTagCompound tag = tagList.getCompound(i);

            int index = tag.getByte("RecipeIndex");

            if (index >= 0 && index < this.recipes.length)
            {
                this.recipes[index].readFromNBT(tag);
            }
        }

        this.changeSelectedRecipe(nbt.getByte("Selected"));
    }

    private NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbt)
    {
        NBTTagList tagRecipes = new NBTTagList();

        for (int i = 0; i < this.recipes.length; i++)
        {
            if (this.recipes[i].isValid())
            {
                NBTTagCompound tag = new NBTTagCompound();
                tag.putByte("RecipeIndex", (byte) i);
                this.recipes[i].writeToNBT(tag);
                tagRecipes.add(tag);
            }
        }

        nbt.put("Recipes", tagRecipes);
        nbt.putByte("Selected", (byte) this.selected);

        return nbt;
    }

    private String getFileName()
    {
        String name = "recipes.nbt";

        if (this.global == false)
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.isSingleplayer())
            {
                IntegratedServer server = mc.getIntegratedServer();

                if (server != null)
                {
                    name = "recipes_" + server.getFolderName() + ".nbt";
                }
            }
            else
            {
                ServerData server = mc.getCurrentServerData();

                if (server != null)
                {
                    name = "recipes_" + server.serverIP.replace(':', '_') + ".nbt";
                }
            }
        }

        return name;
    }

    private File getSaveDir()
    {
        return new File(Minecraft.getInstance().gameDir, Reference.MOD_ID);
    }

    public void readFromDisk()
    {
        try
        {
            File saveDir = this.getSaveDir();

            if (saveDir != null)
            {
                File file = new File(saveDir, this.getFileName());

                if (file.exists() && file.isFile() && file.canRead())
                {
                    FileInputStream is = new FileInputStream(file);
                    this.readFromNBT(CompressedStreamTools.readCompressed(is));
                    is.close();
                    //ItemScroller.logger.info("Read recipes from file '{}'", file.getPath());
                }
            }
        }
        catch (Exception e)
        {
            ItemScroller.logger.warn("Failed to read recipes from file", e);
        }
    }

    public void writeToDisk()
    {
        if (this.dirty)
        {
            try
            {
                File saveDir = this.getSaveDir();

                if (saveDir == null)
                {
                    return;
                }

                if (saveDir.exists() == false)
                {
                    if (saveDir.mkdirs() == false)
                    {
                        ItemScroller.logger.warn("Failed to create the recipe storage directory '{}'", saveDir.getPath());
                        return;
                    }
                }

                File fileTmp  = new File(saveDir, this.getFileName() + ".tmp");
                File fileReal = new File(saveDir, this.getFileName());
                FileOutputStream os = new FileOutputStream(fileTmp);
                CompressedStreamTools.writeCompressed(this.writeToNBT(new NBTTagCompound()), os);
                os.close();

                if (fileReal.exists())
                {
                    fileReal.delete();
                }

                fileTmp.renameTo(fileReal);
                this.dirty = false;
            }
            catch (Exception e)
            {
                ItemScroller.logger.warn("Failed to write recipes to file!", e);
            }
        }
    }
}
