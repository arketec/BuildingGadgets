package com.direwolf20.buildinggadgets.blocks.templatemanager;

import com.direwolf20.buildinggadgets.items.CopyPasteTool;
import com.direwolf20.buildinggadgets.items.Template;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class TemplateManagerContainer extends Container {
    private TemplateManagerTileEntity te;

    public TemplateManagerContainer(IInventory playerInventory, TemplateManagerTileEntity te) {
        this.te = te;

        // This container references items out of our own inventory (the 9 slots we hold ourselves)
        // as well as the slots from the player inventory so that the user can transfer items between
        // both inventories. The two calls below make sure that slots are defined for both inventories.
        addOwnSlots();
        addPlayerSlots(playerInventory);
    }

    private void addPlayerSlots(IInventory playerInventory) {
        // Slots for the main inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = 8 + col * 18;
                int y = row * 18 + 84;
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 10, x, y));
            }
        }

        // Slots for the hotbar
        for (int row = 0; row < 9; ++row) {
            int x = 8 + row * 18;
            int y = 58 + 84;
            this.addSlotToContainer(new Slot(playerInventory, row, x, y));
        }
    }

    private void addOwnSlots() {
        IItemHandler itemHandler = this.te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        int x = 94;
        int y = 35;

        // Add our own slots
        //int slotIndex = 0;
        //for (int i = 0; i < itemHandler.getSlots(); i++) {
        addSlotToContainer(new SlotItemHandler(itemHandler, 0, x, y));
        x = 146;
        addSlotToContainer(new SlotItemHandler(itemHandler, 1, x, y));
        //slotIndex++;

        //}
    }

    @Nullable
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            if (!(itemstack1.getItem() instanceof CopyPasteTool) && !itemstack1.getItem().equals(Items.PAPER) && !(itemstack1.getItem() instanceof Template))
                return itemstack;
            itemstack = itemstack1.copy();

            if (index < TemplateManagerTileEntity.SIZE) {
                if (!this.mergeItemStack(itemstack1, TemplateManagerTileEntity.SIZE, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.mergeItemStack(itemstack1, 0, TemplateManagerTileEntity.SIZE, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return te.canInteractWith(playerIn);
    }
}
