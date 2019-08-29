package com.direwolf20.buildinggadgets.common.items.gadgets;


import com.direwolf20.buildinggadgets.common.BuildingGadgets;
import com.direwolf20.buildinggadgets.common.capability.CapabilityProviderEnergy;
import com.direwolf20.buildinggadgets.common.capability.provider.CapabilityProviderBlockProvider;
import com.direwolf20.buildinggadgets.common.capability.provider.MultiCapabilityProvider;
import com.direwolf20.buildinggadgets.common.commands.CopyUnloadedCommand;
import com.direwolf20.buildinggadgets.common.config.Config;
import com.direwolf20.buildinggadgets.common.items.gadgets.renderers.BaseRenderer;
import com.direwolf20.buildinggadgets.common.save.UndoWorldSave;
import com.direwolf20.buildinggadgets.common.util.CapabilityUtil.EnergyUtil;
import com.direwolf20.buildinggadgets.common.util.blocks.RegionSnapshot;
import com.direwolf20.buildinggadgets.common.util.exceptions.CapabilityNotPresentException;
import com.direwolf20.buildinggadgets.common.util.helpers.NBTHelper;
import com.direwolf20.buildinggadgets.common.util.lang.MessageTranslation;
import com.direwolf20.buildinggadgets.common.util.lang.Styles;
import com.direwolf20.buildinggadgets.common.util.lang.TooltipTranslation;
import com.direwolf20.buildinggadgets.common.util.ref.NBTKeys;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.direwolf20.buildinggadgets.common.util.GadgetUtils.withSuffix;

public abstract class AbstractGadget extends Item {

    private BaseRenderer renderer;

    public AbstractGadget(Properties builder) {
        super(builder);
        renderer = DistExecutor.runForDist(this::createRenderFactory, () -> () -> null);
    }

    public abstract int getEnergyMax();
    public abstract int getEnergyCost(ItemStack tool);

    @OnlyIn(Dist.CLIENT)
    public BaseRenderer getRender() {
        return renderer;
    }

    protected abstract Supplier<BaseRenderer> createRenderFactory();

    protected abstract UndoWorldSave getUndoSave();

    protected void addCapabilityProviders(ImmutableList.Builder<ICapabilityProvider> providerBuilder, ItemStack stack, @Nullable CompoundNBT tag) {
        providerBuilder.add(new CapabilityProviderEnergy(stack, this::getEnergyMax));
        providerBuilder.add(new CapabilityProviderBlockProvider(stack));
    }

    @Override
    @Nullable
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundNBT tag) {
        ImmutableList.Builder<ICapabilityProvider> providerBuilder = ImmutableList.builder();
        addCapabilityProviders(providerBuilder, stack, tag);
        return new MultiCapabilityProvider(providerBuilder.build());
    }

    @Override
    public boolean isDamageable() {
        return getMaxDamage() > 0;
    }

    /*@Override
    public boolean isRepairable() {
        return false;
    }*/

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return EnergyUtil.returnDoubleIfPresent(stack,
                (energy -> 1D - (energy.getEnergyStored() / (double) energy.getMaxEnergyStored())),
                () -> super.getDurabilityForDisplay(stack));
    }

    @Override
    public int getRGBDurabilityForDisplay(ItemStack stack) {
        return EnergyUtil.returnIntIfPresent(stack,
                (energy -> MathHelper.hsvToRGB(Math.max(0.0F, energy.getEnergyStored() / (float) energy.getMaxEnergyStored()) / 3.0F, 1.0F, 1.0F)),
                () -> super.getRGBDurabilityForDisplay(stack));
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return EnergyUtil.returnBooleanIfPresent(stack,
                energy -> energy.getEnergyStored() != energy.getMaxEnergyStored(),
                () -> super.isDamaged(stack));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(NBTKeys.CREATIVE_MARKER))
            return false;

        return EnergyUtil.returnBooleanIfPresent(stack,
                energy -> energy.getEnergyStored() != energy.getMaxEnergyStored(),
                () -> super.showDurabilityBar(stack));
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return !EnergyUtil.hasCap(toRepair) && repair.getItem() == Items.DIAMOND;
    }

    public static ItemStack getGadget(PlayerEntity player) {
        ItemStack heldItem = player.getHeldItemMainhand();
        if (!(heldItem.getItem() instanceof AbstractGadget)) {
            heldItem = player.getHeldItemOffhand();
            if (!(heldItem.getItem() instanceof AbstractGadget)) {
                return ItemStack.EMPTY;
            }
        }
        return heldItem;
    }

    public boolean canUse(ItemStack tool, PlayerEntity player) {
        if (player.isCreative())
            return true;

        IEnergyStorage energy = EnergyUtil.getCap(tool).orElseThrow(CapabilityNotPresentException::new);
        return getEnergyCost(tool) <= energy.getEnergyStored();
    }

    public void applyDamage(ItemStack tool, ServerPlayerEntity player) {
        if (player.isCreative())
            return;

        IEnergyStorage energy = EnergyUtil.getCap(tool).orElseThrow(CapabilityNotPresentException::new);
        energy.extractEnergy(getEnergyCost(tool), false);
    }

    protected void addEnergyInformation(List<ITextComponent> tooltip, ItemStack stack) {
        if (Config.isServerConfigLoaded())
            stack.getCapability(CapabilityEnergy.ENERGY).ifPresent(energy -> {
                tooltip.add(TooltipTranslation.GADGET_ENERGY
                                    .componentTranslation(withSuffix(energy.getEnergyStored()), withSuffix(energy.getMaxEnergyStored()))
                                    .setStyle(Styles.WHITE));
            });
    }

    public static boolean getFuzzy(ItemStack stack) {
        return NBTHelper.getOrNewTag(stack).getBoolean(NBTKeys.GADGET_FUZZY);
    }

    public static void toggleFuzzy(PlayerEntity player, ItemStack stack) {
        NBTHelper.getOrNewTag(stack).putBoolean(NBTKeys.GADGET_FUZZY, !getFuzzy(stack));
        player.sendStatusMessage(new StringTextComponent(TextFormatting.AQUA + new TranslationTextComponent("message.gadget.fuzzymode").getUnformattedComponentText() + ": " + getFuzzy(stack)), true);
    }

    public static boolean getConnectedArea(ItemStack stack) {
        return !NBTHelper.getOrNewTag(stack).getBoolean(NBTKeys.GADGET_UNCONNECTED_AREA);
    }

    public static void toggleConnectedArea(PlayerEntity player, ItemStack stack) {
        NBTHelper.getOrNewTag(stack).putBoolean(NBTKeys.GADGET_UNCONNECTED_AREA, getConnectedArea(stack));
        String suffix = stack.getItem() instanceof GadgetDestruction ? "area" : "surface";
        player.sendStatusMessage(new StringTextComponent(TextFormatting.AQUA + new TranslationTextComponent("message.gadget.connected" + suffix).getUnformattedComponentText() + ": " + getConnectedArea(stack)), true);
    }

    public static boolean shouldRayTraceFluid(ItemStack stack) {
        return NBTHelper.getOrNewTag(stack).getBoolean(NBTKeys.GADGET_RAYTRACE_FLUID);
    }

    public static void toggleRayTraceFluid(ServerPlayerEntity player, ItemStack stack) {
        NBTHelper.getOrNewTag(stack).putBoolean(NBTKeys.GADGET_RAYTRACE_FLUID, !shouldRayTraceFluid(stack));
        player.sendStatusMessage(new StringTextComponent(TextFormatting.AQUA + new TranslationTextComponent("message.gadget.raytrace_fluid").getUnformattedComponentText() + ": " + shouldRayTraceFluid(stack)), true);
    }

    public static void addInformationRayTraceFluid(List<ITextComponent> tooltip, ItemStack stack) {
        tooltip.add(TooltipTranslation.GADGET_RAYTRACE_FLUID
                            .componentTranslation(String.valueOf(shouldRayTraceFluid(stack)))
                            .setStyle(Styles.BLUE));
    }

    protected UUID getUUID(ItemStack stack) {
        CompoundNBT nbt = NBTHelper.getOrNewTag(stack);
        if (! nbt.hasUniqueId(NBTKeys.GADGET_UUID)) {
            UUID newId = getUndoSave().getFreeUUID();
            nbt.putUniqueId(NBTKeys.GADGET_UUID, newId);
            return newId;
        }
        return nbt.getUniqueId(NBTKeys.GADGET_UUID);
    }

    protected static String formatName(String name) {
        return name.replaceAll("(?=[A-Z])", " ").trim();
    }

    protected void addUndo(ItemStack stack, RegionSnapshot snapshot) {
        UndoWorldSave save = getUndoSave();
        save.insertSnapshot(getUUID(stack), snapshot);
    }

    public void undo(World world, PlayerEntity player, ItemStack stack) {
        UndoWorldSave save = getUndoSave();
        Optional<RegionSnapshot> snapshotOptional = save.getSnapshot(getUUID(stack));
        if (snapshotOptional.isPresent()) {
            RegionSnapshot snapshot = snapshotOptional.orElseThrow(RuntimeException::new);
            if (! CopyUnloadedCommand.mayCopyUnloadedChunks(player)) {//TODO separate command
                ImmutableSortedSet<ChunkPos> unloadedChunks = snapshot.getPositions().getBoundingBox().getUnloadedChunks(world);
                if (! unloadedChunks.isEmpty()) {
                    //TODO Proper message
                    player.sendStatusMessage(MessageTranslation.COPY_UNLOADED.componentTranslation().setStyle(Styles.RED), true);
                    BuildingGadgets.LOG.error("Player attempted to undo a Region missing {} unloaded chunks. Denied undo!", unloadedChunks.size());
                    BuildingGadgets.LOG.trace("The following chunks were detected as unloaded {}.", unloadedChunks);
                    return;
                }
            }
            if (snapshot.getDim() != world.getDimension().getType()) {
                player.sendStatusMessage(MessageTranslation.UNDO_FAILED.componentTranslation().setStyle(Styles.RED), true);
                return;
            }
            snapshot.restore(world);
        } else
            player.sendStatusMessage(MessageTranslation.NOTHING_TO_UNDO.componentTranslation().setStyle(Styles.RED), true);
    }
}
