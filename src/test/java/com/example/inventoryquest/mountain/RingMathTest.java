package com.example.inventoryquest.mountain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RingMathTest {

    @Test
    void ringSizesShrinkByFourEachLevel() {
        assertThat(RingMath.squaresAt(0)).isEqualTo(256);
        assertThat(RingMath.squaresAt(1)).isEqualTo(64);
        assertThat(RingMath.squaresAt(2)).isEqualTo(16);
        assertThat(RingMath.squaresAt(3)).isEqualTo(4);
        assertThat(RingMath.squaresAt(4)).isEqualTo(1);
    }

    @Test
    void leftAndRightWrapAroundTheRing() {
        Position base = new Position(0, 0);
        assertThat(RingMath.left(base)).isEqualTo(new Position(0, 255));
        assertThat(RingMath.right(new Position(0, 255))).isEqualTo(new Position(0, 0));
        assertThat(RingMath.right(base)).isEqualTo(new Position(0, 1));
    }

    @Test
    void upClimbsIntoTheParentSquare() {
        // squares 4,5,6,7 on level 0 all feed square 1 on level 1
        assertThat(RingMath.up(new Position(0, 4))).isEqualTo(new Position(1, 1));
        assertThat(RingMath.up(new Position(0, 7))).isEqualTo(new Position(1, 1));
        assertThat(RingMath.up(new Position(0, 8))).isEqualTo(new Position(1, 2));
    }

    @Test
    void everyBaseSquareEventuallyReachesTheSingleSummit() {
        for (int start = 0; start < 256; start += 37) {
            Position p = new Position(0, start);
            while (p.level() < RingMath.SUMMIT_LEVEL) {
                p = RingMath.up(p);
            }
            assertThat(p).isEqualTo(new Position(4, 0));
            assertThat(p.isSummit()).isTrue();
        }
    }

    @Test
    void thereIsNoUpFromTheSummit() {
        Position summit = new Position(4, 0);
        assertThat(RingMath.canMove(summit, Direction.UP)).isFalse();
        assertThat(RingMath.canMove(summit, Direction.LEFT)).isTrue();
        assertThatThrownBy(() -> RingMath.up(summit)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void downLandsOnOneOfTheFourChildSquares_theInverseOfUp() {
        // square 1 on level 1 is fed by squares 4,5,6,7 on level 0
        Position parent = new Position(1, 1);
        assertThat(RingMath.down(parent, 0)).isEqualTo(new Position(0, 4));
        assertThat(RingMath.down(parent, 3)).isEqualTo(new Position(0, 7));
        // and each of those climbs right back up to the parent
        for (int child = 0; child < RingMath.MERGE_FACTOR; child++) {
            assertThat(RingMath.up(RingMath.down(parent, child))).isEqualTo(parent);
        }
    }

    @Test
    void thereIsNoDownFromTheBase() {
        Position base = new Position(0, 10);
        assertThat(RingMath.canMove(base, Direction.DOWN)).isFalse();
        assertThat(RingMath.canMove(new Position(1, 0), Direction.DOWN)).isTrue();
        assertThatThrownBy(() -> RingMath.down(base, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void positionRejectsOutOfRangeIndex() {
        assertThatThrownBy(() -> new Position(4, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Position(1, 64)).isInstanceOf(IllegalArgumentException.class);
    }
}
