package com.example.inventoryquest.mountain;

import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClimbGearTest {

    @Test
    void eachAscentDemandsItsOwnGear() {
        assertThat(ClimbGear.requiredToLeave(0)).contains(ItemType.SNOW_JACKET);
        assertThat(ClimbGear.requiredToLeave(1)).contains(ItemType.CLEATS);
        assertThat(ClimbGear.requiredToLeave(2)).contains(ItemType.ICE_PICK);
        assertThat(ClimbGear.requiredToLeave(3)).contains(ItemType.OXYGEN_TANK);
    }

    @Test
    void theSummitDemandsNothingBecauseThereIsNoHigher() {
        assertThat(ClimbGear.requiredToLeave(RingMath.SUMMIT_LEVEL)).isEmpty();
    }

    @Test
    void allClimbGearIsMarkedAsGear() {
        assertThat(ItemType.SNOW_JACKET.isGear()).isTrue();
        assertThat(ItemType.OXYGEN_TANK.isGear()).isTrue();
        assertThat(ItemType.SNOW_JACKET.displayName()).isEqualTo("snow jacket");
    }

    @Test
    void everyPieceOfClimbGearIsEquippableIntoItsOwnSlot() {
        assertThat(ItemType.SNOW_JACKET.isEquippable()).isTrue();
        assertThat(ItemType.SNOW_JACKET.equipSlot()).isEqualTo(EquipSlot.JACKET);
        assertThat(ItemType.CLEATS.equipSlot()).isEqualTo(EquipSlot.BOOTS);
        assertThat(ItemType.ICE_PICK.equipSlot()).isEqualTo(EquipSlot.PICK);
        assertThat(ItemType.OXYGEN_TANK.equipSlot()).isEqualTo(EquipSlot.TANK);
    }
}
