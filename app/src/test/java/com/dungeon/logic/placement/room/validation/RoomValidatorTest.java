package com.dungeon.logic.placement.room.validation;

import com.dungeon.domain.Room;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RoomValidator}.
 */
@DisplayName("RoomValidator")
class RoomValidatorTest {

    // -------------------------------------------------------------------------
    // Helpers: stub factories
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal Room mock with AABB centred at (cx, cy), half-size hw x hh,
     * z-interval [zMin, zMax], wallThickness = 0 and floorThickness = 0.
     */
    private static Room room(float cx, float cy, float hw, float hh,
                             float zMin, float zMax) {
        Room r = mock(Room.class);
        // outer corners: unit square around (cx, cy)
        List<Vector2f> corners = List.of(
                new Vector2f(cx - hw, cy - hh),
                new Vector2f(cx + hw, cy - hh),
                new Vector2f(cx + hw, cy + hh),
                new Vector2f(cx - hw, cy + hh)
        );
        when(r.getInnerCorners()).thenReturn(corners);
        when(r.getOuterCorners()).thenReturn(corners);
        when(r.getWallThickness()).thenReturn(0f);
        when(r.getZLevel()).thenReturn(zMin);
        when(r.getHeight()).thenReturn(zMax - zMin);
        when(r.getFloorThickness()).thenReturn(0f);
        return r;
    }

    // -------------------------------------------------------------------------
    // isValid - empty existing list
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isValid - no existing rooms")
    class NoExistingRooms {

        @Test
        @DisplayName("any candidate is valid when existing list is empty")
        void alwaysValidWhenEmpty() {
            Room candidate = room(0, 0, 1, 1, 0, 2);
            assertThat(RoomValidator.isValid(candidate, List.of())).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // possiblyCollides - 2D AABB overlap
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("possiblyCollides - 2D AABB")
    class AabbOverlap {

        @Test
        @DisplayName("clearly overlapping rooms collide")
        void overlapping() {
            Room a = room(0, 0, 2, 2, 0, 1);
            Room b = room(1, 1, 2, 2, 0, 1);
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }

        @Test
        @DisplayName("clearly separated rooms do not collide")
        void separated() {
            Room a = room(0, 0, 1, 1, 0, 1);
            Room b = room(10, 0, 1, 1, 0, 1);
            assertThat(RoomValidator.possiblyCollides(a, b)).isFalse();
        }

        @Test
        @DisplayName("rooms touching exactly on their boundary -> JTS Envelope.intersects treats touching as intersecting")
        void touching() {
            // a ends at x=1, b starts at x=1 (touching, not overlapping)
            Room a = room(0, 0, 1, 1, 0, 1);
            Room b = room(2, 0, 1, 1, 0, 1);
            // gap = 0 but not overlapping -> should collide
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }

        @Test
        @DisplayName("wall thickness expansion can cause collision for rooms that would not strictly overlap")
        void wallThicknessExpands() {
            Room a = mock(Room.class);
            Room b = mock(Room.class);

            // a: unit square [0,1]x[0,1], wall=1  -> expanded to [-1,2]x[-1,2]
            when(a.getInnerCorners()).thenReturn(List.of(
                    new Vector2f(0,0), new Vector2f(1,0),
                    new Vector2f(1,1), new Vector2f(0,1)));
            when(a.getOuterCorners()).thenReturn(List.of(
                    new Vector2f(-1,-1), new Vector2f(2,-1),
                    new Vector2f(2,2), new Vector2f(-1,2)));
            when(a.getWallThickness()).thenReturn(1f);
            when(a.getZLevel()).thenReturn(0.0f);
            when(a.getHeight()).thenReturn(1.0f);
            when(a.getFloorThickness()).thenReturn(0f);

            // b: unit square [3,4]x[0,1], wall=1 -> expanded to [2,5]x[-1,2]
            when(b.getInnerCorners()).thenReturn(List.of(
                    new Vector2f(3,0), new Vector2f(4,0),
                    new Vector2f(4,1), new Vector2f(3,1)));
            when(b.getOuterCorners()).thenReturn(List.of(
                    new Vector2f(2,-1), new Vector2f(2,2),
                    new Vector2f(5,2), new Vector2f(5,-1)));
            when(b.getWallThickness()).thenReturn(1f);
            when(b.getZLevel()).thenReturn(0.0f);
            when(b.getHeight()).thenReturn(1.0f);
            when(b.getFloorThickness()).thenReturn(0f);

            // Both expansions reach x=2 simultaneously -> touching envelopes intersect in JTS
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // possiblyCollides - Z overlap
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("possiblyCollides - Z interval")
    class ZOverlap {

        @Test
        @DisplayName("rooms on different floors do not collide")
        void differentFloors() {
            Room a = room(0, 0, 5, 5, 0,  2);
            Room b = room(0, 0, 5, 5, 5, 10);
            assertThat(RoomValidator.possiblyCollides(a, b)).isFalse();
        }

        @Test
        @DisplayName("rooms on same floor with same 2D footprint collide")
        void sameFloor() {
            Room a = room(0, 0, 5, 5, 0, 2);
            Room b = room(0, 0, 5, 5, 0, 2);
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }

        @Test
        @DisplayName("partially overlapping Z intervals trigger collision when 2D also overlaps")
        void partialZOverlap() {
            Room a = room(0, 0, 3, 3, 0, 5);
            Room b = room(0, 0, 3, 3, 4, 8);
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }

        @Test
        @DisplayName("floor thickness extends Z interval for collision detection")
        void floorThicknessExtends() {
            Room a = mock(Room.class);
            Room b = mock(Room.class);

            // a: z=[0,2], floor=1 -> effective [−1, 3]
            when(a.getOuterCorners()).thenReturn(List.of(
                    new Vector2f(0,0), new Vector2f(1,0),
                    new Vector2f(1,1), new Vector2f(0,1)));
            when(a.getWallThickness()).thenReturn(0f);
            when(a.getZLevel()).thenReturn(0.0f);
            when(a.getHeight()).thenReturn(2.0f);
            when(a.getFloorThickness()).thenReturn(1f);

            // b: z=[3.5,5], floor=1 -> effective [2.5, 6]
            when(b.getOuterCorners()).thenReturn(List.of(
                    new Vector2f(0,0), new Vector2f(1,0),
                    new Vector2f(1,1), new Vector2f(0,1)));
            when(b.getWallThickness()).thenReturn(0f);
            when(b.getZLevel()).thenReturn(3.5f);
            when(b.getHeight()).thenReturn(1.5f);
            when(b.getFloorThickness()).thenReturn(1f);

            // Without floor: [0,2] vs [3.5,5] -> no overlap
            // With floor:   [-1,3] vs [2.5,6] -> overlap (3 > 2.5)
            assertThat(RoomValidator.possiblyCollides(a, b)).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // isValid - multiple existing rooms
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isValid - multiple existing rooms")
    class MultipleExisting {

        @Test
        @DisplayName("candidate colliding with only one of many rooms is rejected")
        void rejectIfCollidesWithOne() {
            Room candidate  = room( 0,  0, 5, 5, 0, 2);
            Room noConflict = room(20, 20, 2, 2, 0, 2);
            Room conflict   = room( 0,  0, 5, 5, 0, 2);
            assertThat(RoomValidator.isValid(candidate, List.of(noConflict, conflict))).isFalse();
        }

        @Test
        @DisplayName("candidate not colliding with any existing room is valid")
        void acceptIfNoCollision() {
            Room candidate = room(100, 100, 1, 1, 0, 2);
            Room r1 = room(0,  0, 3, 3, 0, 2);
            Room r2 = room(10, 0, 3, 3, 0, 2);
            assertThat(RoomValidator.isValid(candidate, List.of(r1, r2))).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Symmetry: possiblyCollides(a, b) == possiblyCollides(b, a)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Symmetry")
    class Symmetry {

        @Test
        @DisplayName("collision check is symmetric")
        void symmetric() {
            Room a = room(0,  0, 3, 3, 0, 2);
            Room b = room(2,  2, 3, 3, 0, 2);
            assertThat(RoomValidator.possiblyCollides(a, b))
                    .isEqualTo(RoomValidator.possiblyCollides(b, a));
        }

        @Test
        @DisplayName("no-collision check is symmetric")
        void symmetricNonCollision() {
            Room a = room(0,  0, 1, 1, 0,  2);
            Room b = room(20, 0, 1, 1, 50, 60);
            assertThat(RoomValidator.possiblyCollides(a, b))
                    .isEqualTo(RoomValidator.possiblyCollides(b, a));
        }
    }
}