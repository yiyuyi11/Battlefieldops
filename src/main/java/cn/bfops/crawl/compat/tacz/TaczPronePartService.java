package cn.bfops.crawl.compat.tacz;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import cn.bfops.crawl.CrawlConfig;

public final class TaczPronePartService {
   private static final Map<Player, TaczPronePartService.ProneParts> PRONE_PARTS_BY_PLAYER = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Player, TaczPronePartService.StandingParts> STANDING_PARTS_BY_PLAYER = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Player, TaczPronePartService.LastSafeProneState> LAST_SAFE_PRONE_STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Player, TaczPronePartService.PendingProneTransition> PENDING_PRONE_TRANSITIONS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Player, TaczPronePartService.RotationGuardState> ROTATION_GUARD_STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final double STANDING_FOOT_BOX_HEIGHT = 0.35;
   private static final double CROUCHING_FOOT_BOX_HEIGHT = 0.24499999999999997;
   private static final double STANDING_FOOT_BOX_INFLATE = 0.015;
   private static final double STANDING_LEG_BOX_HEIGHT = 0.55;
   private static final double CROUCHING_LEG_BOX_HEIGHT = 0.33;
   private static final float FOOT_DAMAGE_MULTIPLIER = 0.7F;
   private static final float LEG_DAMAGE_MULTIPLIER = 0.8F;
   private static final double POSITION_EPSILON = 0.001;
   private static final double POSITION_EPSILON_SQR = 1.0E-6;
   private static final int PRONE_RECOVERY_BINARY_STEPS = 5;
   private static final double PRONE_BACKOFF_DISTANCE = 0.25;
   private static final double PRONE_ENTRY_MAX_RADIUS = 0.75;
   private static final double PRONE_ENTRY_SEARCH_STEP = 0.05;
   private static final double PRONE_ENTRY_SMOOTH_FACTOR = 0.35;
   private static final float ROTATION_SAMPLE_STEP_DEGREES = 1.5F;
   private static final float ROTATION_EPSILON_DEGREES = 0.25F;
   private static final int ROTATION_GUARD_BINARY_STEPS = 6;
   private static final float ROTATION_BLOCKED_PUSH_MIN_STEP = 0.08F;
   private static final float ROTATION_RETURN_STEP = 1.2F;
   private static final float ROTATION_RETURN_EXTRA_FACTOR = 0.18F;

   private TaczPronePartService() {
   }

   public static void clearAll() {
      PRONE_PARTS_BY_PLAYER.clear();STANDING_PARTS_BY_PLAYER.clear();LAST_SAFE_PRONE_STATES.clear();
      PENDING_PRONE_TRANSITIONS.clear();ROTATION_GUARD_STATES.clear();
   }

   public static void onPlayerTick(Player player) {
      if (player != null && player.isAlive()) {
         if (isProneLike(player)) {
            STANDING_PARTS_BY_PLAYER.remove(player);
            applyPendingProneTransition(player);
            applyRotationGuard(player);
            TaczPronePartService.ProneParts parts = PRONE_PARTS_BY_PLAYER.computeIfAbsent(player, id -> new TaczPronePartService.ProneParts(player));
            if (parts.parent != player) {
               PRONE_PARTS_BY_PLAYER.put(player, new TaczPronePartService.ProneParts(player));
               parts = PRONE_PARTS_BY_PLAYER.get(player);
            }

            parts.refresh();
            if (!recoverProneOverlap(player, parts)) {
               rememberSafeProneState(player);
            }
         } else {
            PRONE_PARTS_BY_PLAYER.remove(player);
            ROTATION_GUARD_STATES.remove(player);
            if (!hasStandingFootBox(player)) {
               STANDING_PARTS_BY_PLAYER.remove(player);
            } else {
               TaczPronePartService.StandingParts partsx = STANDING_PARTS_BY_PLAYER.computeIfAbsent(
                  player, id -> new TaczPronePartService.StandingParts(player)
               );
               if (partsx.parent != player) {
                  STANDING_PARTS_BY_PLAYER.put(player, new TaczPronePartService.StandingParts(player));
                  partsx = STANDING_PARTS_BY_PLAYER.get(player);
               }

               partsx.refresh();
            }
         }
      }
   }

   public static boolean prepareProneEntry(Player player) {
      if (player != null && player.isAlive()) {
         Vec3 currentPos = player.position();
         float yaw = player.getYRot();
         Vec3 targetPos = findNearestProneEntryPosition(player, currentPos, yaw);
         if (targetPos == null) {
            PENDING_PRONE_TRANSITIONS.remove(player);
            ROTATION_GUARD_STATES.remove(player);
            return false;
         } else {
            if (targetPos.distanceToSqr(currentPos) > 1.0E-6) {
               PENDING_PRONE_TRANSITIONS.put(player, new TaczPronePartService.PendingProneTransition(targetPos));
               LAST_SAFE_PRONE_STATES.put(player, new TaczPronePartService.LastSafeProneState(targetPos, yaw));
            } else {
               PENDING_PRONE_TRANSITIONS.remove(player);
               LAST_SAFE_PRONE_STATES.put(player, new TaczPronePartService.LastSafeProneState(currentPos, yaw));
            }

            ROTATION_GUARD_STATES.put(player, new TaczPronePartService.RotationGuardState(yaw));
            return true;
         }
      } else {
         return false;
      }
   }

   public static void onCrawlStateChanged(Player player, boolean crawling) {
      if (player != null) {
         if (crawling) {
            ROTATION_GUARD_STATES.computeIfAbsent(player, id -> new TaczPronePartService.RotationGuardState(player.getYRot()));
         } else {
            PENDING_PRONE_TRANSITIONS.remove(player);
            ROTATION_GUARD_STATES.remove(player);
            LAST_SAFE_PRONE_STATES.remove(player);
         }
      }
   }

   public static void clearPlayer(Player player) {
      if (player != null) {
         PRONE_PARTS_BY_PLAYER.remove(player);
         STANDING_PARTS_BY_PLAYER.remove(player);
         LAST_SAFE_PRONE_STATES.remove(player);
         PENDING_PRONE_TRANSITIONS.remove(player);
         ROTATION_GUARD_STATES.remove(player);
      }
   }

   public static PartEntity<?>[] getProneParts(Player player) {
      if (player == null || !player.isAlive()) {
         return null;
      } else if (isProneLike(player)) {
         TaczPronePartService.ProneParts parts = PRONE_PARTS_BY_PLAYER.computeIfAbsent(player, id -> new TaczPronePartService.ProneParts(player));
         if (parts.parent != player) {
            parts = new TaczPronePartService.ProneParts(player);
            PRONE_PARTS_BY_PLAYER.put(player, parts);
         }

         parts.refresh();
         return new PartEntity[]{parts.headPart, parts.footPart};
      } else if (!hasStandingFootBox(player)) {
         return null;
      } else {
         TaczPronePartService.StandingParts parts = STANDING_PARTS_BY_PLAYER.computeIfAbsent(player, id -> new TaczPronePartService.StandingParts(player));
         if (parts.parent != player) {
            parts = new TaczPronePartService.StandingParts(player);
            STANDING_PARTS_BY_PLAYER.put(player, parts);
         }

         parts.refresh();
         return new PartEntity[]{parts.legPart, parts.footPart};
      }
   }

   public static TaczPronePartService.ProneHitResult tryResolvePartHit(Entity target, Vec3 startVec, Vec3 endVec) {
      if (!(target instanceof Player player) || !player.isAlive() || startVec == null || endVec == null) {
         return null;
      }

      if (isProneLike(player)) {
         return tryResolveProneHit(player, startVec, endVec);
      } else {
         return !hasStandingFootBox(player) ? null : tryResolveStandingLowerLimbHit(player, startVec, endVec);
      }
   }

   public static Vec3 clipProneMovement(Player player, Vec3 movement) {
      if (player != null && movement != null && !(movement.lengthSqr() <= 1.0E-6) && isProneLike(player)) {
         Vec3 currentPos = player.position();
         float yaw = player.getYRot();
         TaczPronePartService.ProneBoxSet currentBoxes = createProneBoxSet(player, currentPos, yaw);
         boolean currentlyColliding = hasProneBoxCollision(player, currentBoxes.headBox(), currentBoxes.footBox());
         if (!currentlyColliding) {
            rememberSafeProneState(player);
         }

         Vec3 horizontalMovement = new Vec3(movement.x, 0.0, movement.z);
         if (horizontalMovement.lengthSqr() <= 1.0E-6) {
            return movement;
         } else {
            Vec3 recoveryOffset = currentlyColliding ? resolveProneRecoveryOffset(player, currentPos, yaw, horizontalMovement) : Vec3.ZERO;
            Vec3 recoveryStart = currentPos.add(recoveryOffset);
            Vec3 desiredHorizontal = horizontalMovement.subtract(recoveryOffset);
            Vec3 clippedHorizontal = clipProneHorizontalMovement(player, recoveryStart, yaw, desiredHorizontal);
            if (!currentlyColliding) {
               clippedHorizontal = improveProneHorizontalMovement(player, currentPos, yaw, horizontalMovement, clippedHorizontal);
               recoveryOffset = Vec3.ZERO;
            }

            return new Vec3(recoveryOffset.x + clippedHorizontal.x, movement.y, recoveryOffset.z + clippedHorizontal.z);
         }
      } else {
         return movement;
      }
   }

   public static double dampClientYawTurn(Player player, double yawTurnInput) {
      if (player != null && player.isAlive() && isProneLike(player) && !(Math.abs(yawTurnInput) <= 1.0E-6)) {
         Vec3 currentPos = player.position();
         float currentYaw = player.getYRot();
         TaczPronePartService.RotationGuardState state = ROTATION_GUARD_STATES.computeIfAbsent(
            player, id -> new TaczPronePartService.RotationGuardState(currentYaw)
         );
         float baseYaw = state.safeYaw();
         if (findResolvableProneOffset(player, currentPos, baseYaw) == null) {
            TaczPronePartService.LastSafeProneState safeState = LAST_SAFE_PRONE_STATES.get(player);
            if (safeState == null
               || safeState.position().distanceToSqr(currentPos) > 0.0625
               || findResolvableProneOffset(player, currentPos, safeState.yaw()) == null) {
               return yawTurnInput;
            }

            baseYaw = safeState.yaw();
            state.safeYaw = baseYaw;
         }

         float desiredYaw = currentYaw + (float)yawTurnInput * 0.15F;
         if (findResolvableProneOffset(player, currentPos, desiredYaw) != null) {
            state.safeYaw = desiredYaw;
            state.lastAppliedYaw = desiredYaw;
            return yawTurnInput;
         } else {
            float boundaryYaw = findSafeYawBetween(player, currentPos, baseYaw, desiredYaw);
            float adjustedYaw = applyBlockedTurnDamping(currentYaw, baseYaw, boundaryYaw, desiredYaw);
            if (findResolvableProneOffset(player, currentPos, adjustedYaw) == null) {
               adjustedYaw = boundaryYaw;
            }

            state.lastAppliedYaw = adjustedYaw;
            return (double)(Mth.wrapDegrees(adjustedYaw - currentYaw) / 0.15F);
         }
      } else {
         return yawTurnInput;
      }
   }

   private static TaczPronePartService.ProneHitResult tryResolveProneHit(Player player, Vec3 startVec, Vec3 endVec) {
      TaczPronePartService.ProneParts parts = PRONE_PARTS_BY_PLAYER.get(player);
      if (parts == null || parts.parent != player) {
         parts = new TaczPronePartService.ProneParts(player);
         PRONE_PARTS_BY_PLAYER.put(player, parts);
      }

      parts.refresh();
      Vec3 headHit = (Vec3)parts.headBox.clip(startVec, endVec).orElse(null);
      Vec3 bodyHit = (Vec3)parts.bodyBox.clip(startVec, endVec).orElse(null);
      Vec3 footHit = (Vec3)parts.footBox.clip(startVec, endVec).orElse(null);
      Vec3 nearest = null;
      PlayerPronePartEntity selectedPart = null;
      boolean selectedHeadshot = false;
      if (headHit != null) {
         nearest = headHit;
         selectedPart = parts.headPart;
         selectedHeadshot = true;
      }

      if (bodyHit != null && (nearest == null || startVec.distanceToSqr(bodyHit) < startVec.distanceToSqr(nearest))) {
         nearest = bodyHit;
         selectedPart = parts.headPart;
         selectedHeadshot = false;
      }

      if (footHit != null && (nearest == null || startVec.distanceToSqr(footHit) < startVec.distanceToSqr(nearest))) {
         nearest = footHit;
         selectedPart = parts.footPart;
         selectedHeadshot = false;
      }

      return nearest != null && selectedPart != null ? new TaczPronePartService.ProneHitResult(selectedPart, nearest, selectedHeadshot) : null;
   }

   private static TaczPronePartService.ProneHitResult tryResolveStandingLowerLimbHit(Player player, Vec3 startVec, Vec3 endVec) {
      TaczPronePartService.StandingParts parts = STANDING_PARTS_BY_PLAYER.get(player);
      if (parts == null || parts.parent != player) {
         parts = new TaczPronePartService.StandingParts(player);
         STANDING_PARTS_BY_PLAYER.put(player, parts);
      }

      parts.refresh();
      Vec3 footHit = (Vec3)parts.footBox.clip(startVec, endVec).orElse(null);
      Vec3 legHit = (Vec3)parts.legBox.clip(startVec, endVec).orElse(null);
      Vec3 selectedHit = null;
      PlayerPronePartEntity selectedPart = null;
      if (footHit != null) {
         selectedHit = footHit;
         selectedPart = parts.footPart;
      }

      if (legHit != null && (selectedHit == null || startVec.distanceToSqr(legHit) < startVec.distanceToSqr(selectedHit))) {
         selectedHit = legHit;
         selectedPart = parts.legPart;
      }

      if (selectedHit != null && selectedPart != null) {
         Vec3 bodyHit = (Vec3)parts.bodyBox.clip(startVec, endVec).orElse(null);
         return bodyHit != null && startVec.distanceToSqr(bodyHit) + 1.0E-6 < startVec.distanceToSqr(selectedHit)
            ? null
            : new TaczPronePartService.ProneHitResult(selectedPart, selectedHit, false);
      } else {
         return null;
      }
   }

   private static boolean isProneLike(Player player) {
      return player != null && player.getPose() == Pose.SWIMMING && !player.isSwimming();
   }

   private static boolean hasStandingFootBox(Player player) {
      if (player == null) {
         return false;
      } else {
         Pose pose = player.getPose();
         return pose == Pose.STANDING || pose == Pose.CROUCHING;
      }
   }

   private static double mergeMovementComponent(double requested, double first, double second) {
      if (Math.abs(requested) <= 0.001) {
         return 0.0;
      } else {
         double result = requested;
         double requestSign = Math.signum(requested);
         if (!(requestSign * first < 0.0) && !(requestSign * second < 0.0)) {
            if (Math.abs(first) < Math.abs(requested)) {
               result = first;
            }

            if (Math.abs(second) < Math.abs(result)) {
               result = second;
            }

            return result;
         } else {
            return 0.0;
         }
      }
   }

   private static boolean hasProneBoxCollision(Player player, AABB headBox, AABB footBox) {
      return !player.level().noCollision(player, headBox) || !player.level().noCollision(player, footBox);
   }

   private static void rememberSafeProneState(Player player) {
      if (player != null) {
         LAST_SAFE_PRONE_STATES.put(player, new TaczPronePartService.LastSafeProneState(player.position(), player.getYRot()));
         ROTATION_GUARD_STATES.compute(player, (id, state) -> {
            if (state == null) {
               return new TaczPronePartService.RotationGuardState(player.getYRot());
            } else {
               state.safeYaw = player.getYRot();
               state.lastAppliedYaw = player.getYRot();
               return (TaczPronePartService.RotationGuardState)state;
            }
         });
      }
   }

   private static boolean isProneSpaceFree(Player player, Vec3 position, float yaw) {
      TaczPronePartService.ProneBoxSet boxSet = createProneBoxSet(player, position, yaw);
      return !hasProneBoxCollision(player, boxSet.headBox(), boxSet.footBox());
   }

   private static void applyPendingProneTransition(Player player) {
      TaczPronePartService.PendingProneTransition transition = PENDING_PRONE_TRANSITIONS.get(player);
      if (transition != null) {
         Vec3 currentPos = player.position();
         Vec3 targetPos = transition.targetPosition();
         if (currentPos.distanceToSqr(targetPos) <= 1.0E-6) {
            PENDING_PRONE_TRANSITIONS.remove(player);
         } else {
            float yaw = player.getYRot();
            Vec3 nextPos;
            if (!isProneSpaceFree(player, currentPos, yaw)) {
               nextPos = findNearestReachablePronePosition(player, targetPos, currentPos, yaw);
            } else {
               Vec3 desiredPos = currentPos.lerp(targetPos, 0.35);
               nextPos = isProneSpaceFree(player, desiredPos, yaw) ? desiredPos : findNearestReachablePronePosition(player, targetPos, desiredPos, yaw);
            }

            if (nextPos.distanceToSqr(currentPos) <= 1.0E-6) {
               nextPos = targetPos;
            }

            if (!isProneSpaceFree(player, nextPos, yaw) && isProneSpaceFree(player, targetPos, yaw)) {
               nextPos = targetPos;
            }

            applyProneRecoveryPosition(player, nextPos);
            if (nextPos.distanceToSqr(targetPos) <= 1.0E-6) {
               PENDING_PRONE_TRANSITIONS.remove(player);
            }
         }
      }
   }

   private static void applyRotationGuard(Player player) {
      Vec3 currentPos = player.position();
      float requestedYaw = player.getYRot();
      TaczPronePartService.RotationGuardState state = ROTATION_GUARD_STATES.computeIfAbsent(
         player, id -> new TaczPronePartService.RotationGuardState(requestedYaw)
      );
      float baseYaw = state.safeYaw();
      Vec3 baseOffset = findResolvableProneOffset(player, currentPos, baseYaw);
      if (baseOffset == null) {
         TaczPronePartService.LastSafeProneState safeState = LAST_SAFE_PRONE_STATES.get(player);
         if (safeState == null || safeState.position().distanceToSqr(currentPos) > 0.0625) {
            return;
         }

         baseOffset = findResolvableProneOffset(player, currentPos, safeState.yaw());
         if (baseOffset == null) {
            return;
         }

         baseYaw = safeState.yaw();
      }

      if (baseOffset.lengthSqr() > 1.0E-6) {
         currentPos = applyResolvedProneOffset(player, currentPos, baseOffset);
      }

      Vec3 requestedOffset = findResolvableProneOffset(player, currentPos, requestedYaw);
      if (requestedOffset != null) {
         if (requestedOffset.lengthSqr() > 1.0E-6) {
            applyResolvedProneOffset(player, currentPos, requestedOffset);
         }

         state.safeYaw = requestedYaw;
         state.lastAppliedYaw = requestedYaw;
      } else {
         float boundaryYaw = findSafeYawBetween(player, currentPos, baseYaw, requestedYaw);
         float blockedSign = Math.signum(Mth.wrapDegrees(requestedYaw - boundaryYaw));
         float inputDelta = Mth.wrapDegrees(requestedYaw - state.lastAppliedYaw);
         boolean pushingBlockedDirection = Math.abs(inputDelta) > 0.25F && Math.signum(inputDelta) == blockedSign && blockedSign != 0.0F;
         float correctionYaw = pushingBlockedDirection
            ? applyBlockedTurnDamping(state.lastAppliedYaw, baseYaw, boundaryYaw, requestedYaw)
            : applyReleasedTurnReturn(state.lastAppliedYaw, baseYaw);
         Vec3 correctionOffset = findResolvableProneOffset(player, currentPos, correctionYaw);
         if (correctionOffset == null) {
            correctionYaw = boundaryYaw;
            correctionOffset = findResolvableProneOffset(player, currentPos, boundaryYaw);
         }

         if (correctionOffset != null && correctionOffset.lengthSqr() > 1.0E-6) {
            applyResolvedProneOffset(player, currentPos, correctionOffset);
         }

         applyPlayerYaw(player, correctionYaw);
         state.lastAppliedYaw = correctionYaw;
      }
   }

   private static float applyBlockedTurnDamping(float currentYaw, float safeYaw, float boundaryYaw, float requestedYaw) {
      float totalSpan = Math.abs(Mth.wrapDegrees(boundaryYaw - safeYaw));
      if (totalSpan <= 0.25F) {
         return boundaryYaw;
      } else {
         float currentSpan = Math.abs(Mth.wrapDegrees(currentYaw - safeYaw));
         float remainingRatio = 1.0F - Mth.clamp(currentSpan / totalSpan, 0.0F, 1.0F);
         float inputMagnitude = Math.abs(Mth.wrapDegrees(requestedYaw - currentYaw));
         float allowedStep = Math.max(0.08F, inputMagnitude * Math.max(0.12F, remainingRatio * remainingRatio));
         return Mth.approachDegrees(currentYaw, boundaryYaw, allowedStep);
      }
   }

   private static float applyReleasedTurnReturn(float currentYaw, float safeYaw) {
      float distance = Math.abs(Mth.wrapDegrees(currentYaw - safeYaw));
      float returnStep = 1.2F + distance * 0.18F;
      return Mth.approachDegrees(currentYaw, safeYaw, returnStep);
   }

   private static float findSafeYawBetween(Player player, Vec3 position, float safeYaw, float targetYaw) {
      float delta = Mth.wrapDegrees(targetYaw - safeYaw);
      if (!(Math.abs(delta) <= 0.25F) && findResolvableProneOffset(player, position, targetYaw) == null) {
         int steps = Math.max(1, Mth.ceil(Math.abs(delta) / 1.5F));
         float lastSafeYaw = safeYaw;
         float failedYaw = targetYaw;

         for (int i = 1; i <= steps; i++) {
            float candidateYaw = safeYaw + delta * ((float)i / (float)steps);
            if (findResolvableProneOffset(player, position, candidateYaw) == null) {
               failedYaw = candidateYaw;
               break;
            }

            lastSafeYaw = candidateYaw;
         }

         if (isSameYaw(lastSafeYaw, failedYaw)) {
            return lastSafeYaw;
         } else {
            float low = lastSafeYaw;
            float high = failedYaw;

            for (int i = 0; i < 6; i++) {
               float mid = low + Mth.wrapDegrees(high - low) * 0.5F;
               if (isProneSpaceFree(player, position, mid)) {
                  low = mid;
               } else {
                  high = mid;
               }
            }

            return low;
         }
      } else {
         return targetYaw;
      }
   }

   private static boolean isSameYaw(float first, float second) {
      return Math.abs(Mth.wrapDegrees(first - second)) <= 0.25F;
   }

   private static Vec3 findNearestProneEntryPosition(Player player, Vec3 currentPos, float yaw) {
      if (isProneSpaceFree(player, currentPos, yaw)) {
         return currentPos;
      } else {
         Vec3 best = null;
         double bestDistanceSqr = Double.MAX_VALUE;
         int rings = Math.max(1, Mth.ceil(15.0));
         Vec3[] directions = createProneEntrySearchDirections(yaw);

         for (int ring = 1; ring <= rings; ring++) {
            double radius = Math.min(0.75, (double)ring * 0.05);

            for (Vec3 direction : directions) {
               Vec3 candidatePos = currentPos.add(direction.scale(radius));
               if (isProneSpaceFree(player, candidatePos, yaw)) {
                  Vec3 candidateOffset = candidatePos.subtract(currentPos);
                  Vec3 shortestSafeOffset = findShortestSafeOffset(player, currentPos, yaw, candidateOffset);
                  Vec3 resolvedPos = shortestSafeOffset.lengthSqr() > 1.0E-6 ? currentPos.add(shortestSafeOffset) : candidatePos;
                  double distanceSqr = resolvedPos.distanceToSqr(currentPos);
                  if (distanceSqr < bestDistanceSqr) {
                     best = resolvedPos;
                     bestDistanceSqr = distanceSqr;
                  }
               }
            }

            if (best != null) {
               return best;
            }
         }

         return null;
      }
   }

   private static Vec3 findResolvableProneOffset(Player player, Vec3 currentPos, float yaw) {
      if (isProneSpaceFree(player, currentPos, yaw)) {
         return Vec3.ZERO;
      } else {
         Vec3 recoveryOffset = resolveProneRecoveryOffset(player, currentPos, yaw, Vec3.ZERO);
         if (recoveryOffset.lengthSqr() <= 1.0E-6) {
            return null;
         } else {
            return isProneSpaceFree(player, currentPos.add(recoveryOffset), yaw) ? recoveryOffset : null;
         }
      }
   }

   private static Vec3 applyResolvedProneOffset(Player player, Vec3 currentPos, Vec3 offset) {
      Vec3 resolvedPos = currentPos.add(offset);
      applyProneRecoveryPosition(player, resolvedPos);
      return resolvedPos;
   }

   private static boolean recoverProneOverlap(Player player, TaczPronePartService.ProneParts parts) {
      if (player == null || parts == null) {
         return false;
      } else if (!hasProneBoxCollision(player, parts.headBox, parts.footBox)) {
         return false;
      } else {
         Vec3 currentPos = player.position();
         Vec3 offset = resolveProneRecoveryOffset(player, currentPos, player.getYRot(), Vec3.ZERO);
         if (offset.lengthSqr() <= 1.0E-6) {
            return true;
         } else {
            Vec3 resolvedPos = currentPos.add(offset);
            applyProneRecoveryPosition(player, resolvedPos);
            parts.refresh();
            if (!hasProneBoxCollision(player, parts.headBox, parts.footBox)) {
               LAST_SAFE_PRONE_STATES.put(player, new TaczPronePartService.LastSafeProneState(resolvedPos, player.getYRot()));
               ROTATION_GUARD_STATES.put(player, new TaczPronePartService.RotationGuardState(player.getYRot()));
            }

            return true;
         }
      }
   }

   private static Vec3 resolveProneRecoveryOffset(Player player, Vec3 currentPos, float yaw, Vec3 horizontalMovement) {
      TaczPronePartService.LastSafeProneState safeState = LAST_SAFE_PRONE_STATES.get(player);
      if (safeState != null && isProneSpaceFree(player, safeState.position(), safeState.yaw())) {
         Vec3 rotationOffset = findRotationRecoveryOffset(player, currentPos, yaw, safeState);
         if (rotationOffset.lengthSqr() > 1.0E-6) {
            return rotationOffset;
         }

         if (safeState.position().distanceToSqr(currentPos) > 1.0E-6 && isProneSpaceFree(player, safeState.position(), yaw)) {
            Vec3 nearestSafe = findNearestReachablePronePosition(player, safeState.position(), currentPos, yaw);
            if (nearestSafe != null) {
               return nearestSafe.subtract(currentPos);
            }
         }
      }

      Vec3 reverse = horizontalMovement.lengthSqr() > 1.0E-6 ? horizontalMovement.normalize().scale(-0.25) : horizontalForward(yaw).scale(-0.25);

      for (int i = 1; i <= 4; i++) {
         Vec3 candidate = currentPos.add(reverse.scale((double)i / 4.0));
         if (isProneSpaceFree(player, candidate, yaw)) {
            return candidate.subtract(currentPos);
         }
      }

      return Vec3.ZERO;
   }

   private static Vec3 findRotationRecoveryOffset(Player player, Vec3 currentPos, float yaw, TaczPronePartService.LastSafeProneState safeState) {
      if (safeState == null) {
         return Vec3.ZERO;
      } else {
         TaczPronePartService.ProneBoxSet currentBoxes = createProneBoxSet(player, currentPos, yaw);
         TaczPronePartService.ProneBoxSet safeBoxes = createProneBoxSet(player, safeState.position(), safeState.yaw());
         Vec3[] candidates = new Vec3[]{
            safeBoxes.footBox().getCenter().subtract(currentBoxes.footBox().getCenter()),
            safeBoxes.headBox().getCenter().subtract(currentBoxes.headBox().getCenter()),
            safeState.position().subtract(currentPos)
         };

         for (Vec3 candidate : candidates) {
            Vec3 resolved = findShortestSafeOffset(player, currentPos, yaw, candidate);
            if (resolved.lengthSqr() > 1.0E-6) {
               return resolved;
            }
         }

         return Vec3.ZERO;
      }
   }

   private static Vec3 findShortestSafeOffset(Player player, Vec3 currentPos, float yaw, Vec3 candidateOffset) {
      if (candidateOffset.lengthSqr() <= 1.0E-6) {
         return Vec3.ZERO;
      } else {
         Vec3 safeOffset = null;
         double previousFactor = 0.0;

         for (int i = 1; i <= 4; i++) {
            double factor = (double)i / 4.0;
            Vec3 probeOffset = candidateOffset.scale(factor);
            if (isProneSpaceFree(player, currentPos.add(probeOffset), yaw)) {
               safeOffset = probeOffset;
               double low = previousFactor;
               double high = factor;

               for (int step = 0; step < 5; step++) {
                  double mid = (low + high) * 0.5;
                  Vec3 midOffset = candidateOffset.scale(mid);
                  if (isProneSpaceFree(player, currentPos.add(midOffset), yaw)) {
                     safeOffset = midOffset;
                     high = mid;
                  } else {
                     low = mid;
                  }
               }

               return safeOffset;
            }

            previousFactor = factor;
         }

         return Vec3.ZERO;
      }
   }

   private static Vec3 findNearestReachablePronePosition(Player player, Vec3 safePos, Vec3 currentPos, float yaw) {
      Vec3 resolved = safePos;
      double low = 0.0;
      double high = 1.0;

      for (int i = 0; i < 5; i++) {
         double mid = (low + high) * 0.5;
         Vec3 candidate = safePos.lerp(currentPos, mid);
         if (isProneSpaceFree(player, candidate, yaw)) {
            resolved = candidate;
            low = mid;
         } else {
            high = mid;
         }
      }

      return resolved;
   }

   private static Vec3 clipProneHorizontalMovement(Player player, Vec3 position, float yaw, Vec3 movement) {
      if (movement.lengthSqr() <= 1.0E-6) {
         return Vec3.ZERO;
      } else {
         TaczPronePartService.ProneBoxSet boxSet = createProneBoxSet(player, position, yaw);
         Vec3 headMovement = Entity.collideBoundingBox(player, movement, boxSet.headBox(), player.level(), List.of());
         Vec3 footMovement = Entity.collideBoundingBox(player, movement, boxSet.footBox(), player.level(), List.of());
         return new Vec3(
            mergeMovementComponent(movement.x, headMovement.x, footMovement.x),
            0.0,
            mergeMovementComponent(movement.z, headMovement.z, footMovement.z)
         );
      }
   }

   private static Vec3 improveProneHorizontalMovement(Player player, Vec3 currentPos, float yaw, Vec3 desiredMovement, Vec3 clippedMovement) {
      double desiredLength = desiredMovement.length();
      if (desiredLength <= 0.001) {
         return clippedMovement;
      } else {
         double bestProgress = computeMovementProgress(desiredMovement, clippedMovement);
         if (bestProgress + 0.001 >= desiredLength) {
            return clippedMovement;
         } else {
            Vec3 bestMovement = clippedMovement;
            Vec3 targetPos = currentPos.add(desiredMovement);
            if (!isProneSpaceFree(player, targetPos, yaw)) {
               Vec3 targetRecovery = resolveProneRecoveryOffset(player, targetPos, yaw, desiredMovement);
               bestMovement = chooseBetterProneMovement(player, currentPos, yaw, desiredMovement, clippedMovement, desiredMovement.add(targetRecovery));
            }

            Vec3 backward = horizontalForward(yaw).scale(-1.0);

            for (int i = 1; i <= 6; i++) {
               double distance = 0.25 * (double)i / 3.0;
               Vec3 compensation = backward.scale(distance);
               bestMovement = chooseBetterProneMovement(player, currentPos, yaw, desiredMovement, bestMovement, desiredMovement.add(compensation));
            }

            return bestMovement;
         }
      }
   }

   private static Vec3 chooseBetterProneMovement(Player player, Vec3 currentPos, float yaw, Vec3 desiredMovement, Vec3 currentBest, Vec3 candidateRequest) {
      Vec3 candidateMovement = clipProneHorizontalMovement(player, currentPos, yaw, candidateRequest);
      if (candidateMovement.lengthSqr() <= 1.0E-6) {
         return currentBest;
      } else {
         double desiredLength = desiredMovement.length();
         if (candidateMovement.lengthSqr() > desiredLength * desiredLength + 1.0E-6) {
            candidateMovement = candidateMovement.normalize().scale(desiredLength);
         }

         if (!isProneSpaceFree(player, currentPos.add(candidateMovement), yaw)) {
            return currentBest;
         } else {
            double currentProgress = computeMovementProgress(desiredMovement, currentBest);
            double candidateProgress = computeMovementProgress(desiredMovement, candidateMovement);
            if (candidateProgress > currentProgress + 0.001) {
               return candidateMovement;
            } else {
               return Math.abs(candidateProgress - currentProgress) <= 0.001
                     && candidateMovement.distanceToSqr(desiredMovement) + 1.0E-6 < currentBest.distanceToSqr(desiredMovement)
                  ? candidateMovement
                  : currentBest;
            }
         }
      }
   }

   private static double computeMovementProgress(Vec3 desiredMovement, Vec3 actualMovement) {
      double desiredLength = desiredMovement.length();
      if (!(desiredLength <= 0.001) && actualMovement != null) {
         double progress = actualMovement.dot(desiredMovement.normalize());
         return Math.max(0.0, Math.min(progress, desiredLength));
      } else {
         return 0.0;
      }
   }

   private static void applyProneRecoveryPosition(Player player, Vec3 resolvedPos) {
      player.setPos(resolvedPos.x, resolvedPos.y, resolvedPos.z);
      player.xo = resolvedPos.x;
      player.yo = resolvedPos.y;
      player.zo = resolvedPos.z;
      player.xOld = resolvedPos.x;
      player.yOld = resolvedPos.y;
      player.zOld = resolvedPos.z;
   }

   private static void applyPlayerYaw(Player player, float yaw) {
      player.setYRot(yaw);
      player.setYHeadRot(yaw);
      player.setYBodyRot(yaw);
   }

   private static Vec3 horizontalForward(Entity entity) {
      return horizontalForward(entity.getYRot());
   }

   private static Vec3 horizontalForward(float yaw) {
      double radians = Math.toRadians((double)yaw);
      Vec3 vec = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
      return vec.lengthSqr() < 1.0E-6 ? Vec3.ZERO : vec.normalize();
   }

   private static AABB createProneBodyBox(Player player, Vec3 position) {
      EntityDimensions dimensions = player.getDimensions(Pose.SWIMMING);
      return dimensions.makeBoundingBox(position.x, position.y, position.z);
   }

   private static TaczPronePartService.ProneBoxSet createProneBoxSet(Player player, Vec3 position, float yaw) {
      AABB bodyBox = createProneBodyBox(player, position);
      Vec3 forward = horizontalForward(yaw);
      double headOffset = CrawlConfig.SPEC.isLoaded()?CrawlConfig.PRONE_HEAD_PART_OFFSET.get():.42;
      double footOffset = CrawlConfig.SPEC.isLoaded()?CrawlConfig.PRONE_FOOT_PART_OFFSET.get():.62;
      var boxes=cn.bfops.crawl.ProneGeometry.boxes(bodyBox,yaw,headOffset,footOffset);
      AABB headBox = boxes.head();
      AABB footBox = boxes.feet();
      return new TaczPronePartService.ProneBoxSet(headBox, footBox);
   }

   private static Vec3[] createProneEntrySearchDirections(float yaw) {
      Vec3 forward = horizontalForward(yaw);
      Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
      return new Vec3[]{
         forward,
         forward.add(right).normalize(),
         forward.subtract(right).normalize(),
         right,
         right.scale(-1.0),
         forward.scale(-1.0),
         forward.scale(-1.0).add(right).normalize(),
         forward.scale(-1.0).subtract(right).normalize(),
         new Vec3(1.0, 0.0, 0.0),
         new Vec3(-1.0, 0.0, 0.0),
         new Vec3(0.0, 0.0, 1.0),
         new Vec3(0.0, 0.0, -1.0)
      };
   }

   private static record LastSafeProneState(Vec3 position, float yaw) {
   }

   private static record PendingProneTransition(Vec3 targetPosition) {
   }

   private static record ProneBoxSet(AABB headBox, AABB footBox) {
   }

   public static record ProneHitResult(PlayerPronePartEntity hitPart, Vec3 hitPos, boolean headshot) {
   }

   private static final class ProneParts {
      private final Player parent;
      private final PlayerPronePartEntity headPart;
      private final PlayerPronePartEntity footPart;
      private AABB bodyBox = AABB.ofSize(Vec3.ZERO, 0.6, 0.6, 0.6);
      private AABB headBox = this.bodyBox;
      private AABB footBox = this.bodyBox;

      private ProneParts(Player parent) {
         this.parent = parent;
         this.headPart = new PlayerPronePartEntity(parent, true);
         this.footPart = new PlayerPronePartEntity(parent, false, 0.7F);
      }

      private void refresh() {
         if (this.parent != null) {
            this.bodyBox = TaczPronePartService.createProneBodyBox(this.parent, this.parent.position());
            Vec3 forward = TaczPronePartService.horizontalForward(this.parent);
            double headOffset = CrawlConfig.SPEC.isLoaded()?CrawlConfig.PRONE_HEAD_PART_OFFSET.get():.42;
            double footOffset = CrawlConfig.SPEC.isLoaded()?CrawlConfig.PRONE_FOOT_PART_OFFSET.get():.62;
            var boxes=cn.bfops.crawl.ProneGeometry.boxes(this.bodyBox,this.parent.getYRot(),headOffset,footOffset);
            this.headBox = boxes.head();
            this.footBox = boxes.feet();
            Vec3 hc = this.headBox.getCenter();
            Vec3 fc = this.footBox.getCenter();
            this.headPart.setPos(hc.x, hc.y, hc.z);
            this.footPart.setPos(fc.x, fc.y, fc.z);
            this.headPart.setPartBoundingBox(this.headBox);
            this.footPart.setPartBoundingBox(this.footBox);
         }
      }
   }

   private static final class RotationGuardState {
      private float safeYaw;
      private float lastAppliedYaw;

      private RotationGuardState(float yaw) {
         this.safeYaw = yaw;
         this.lastAppliedYaw = yaw;
      }

      private float safeYaw() {
         return this.safeYaw;
      }
   }

   private static final class StandingParts {
      private final Player parent;
      private final PlayerPronePartEntity footPart;
      private final PlayerPronePartEntity legPart;
      private AABB bodyBox = AABB.ofSize(Vec3.ZERO, 0.6, 1.8, 0.6);
      private AABB footBox = this.bodyBox;
      private AABB legBox = this.bodyBox;

      private StandingParts(Player parent) {
         this.parent = parent;
         this.footPart = new PlayerPronePartEntity(parent, false, 0.7F);
         this.legPart = new PlayerPronePartEntity(parent, false, 0.8F);
      }

      private void refresh() {
         if (this.parent != null) {
            this.bodyBox = this.parent.getBoundingBox();
            double footBoxHeight = this.parent.getPose() == Pose.CROUCHING ? 0.24499999999999997 : 0.35;
            double legBoxHeight = this.parent.getPose() == Pose.CROUCHING ? 0.33 : 0.55;
            double footMaxY = Math.min(this.bodyBox.maxY, this.bodyBox.minY + footBoxHeight);
            this.footBox = new AABB(
               this.bodyBox.minX - 0.015,
               this.bodyBox.minY,
               this.bodyBox.minZ - 0.015,
               this.bodyBox.maxX + 0.015,
               footMaxY,
               this.bodyBox.maxZ + 0.015
            );
            double legMaxY = Math.min(this.bodyBox.maxY, footMaxY + legBoxHeight);
            this.legBox = legMaxY > footMaxY
               ? new AABB(
                  this.bodyBox.minX - 0.015, footMaxY, this.bodyBox.minZ - 0.015, this.bodyBox.maxX + 0.015, legMaxY, this.bodyBox.maxZ + 0.015
               )
               : this.footBox;
            Vec3 fc = this.footBox.getCenter();
            Vec3 lc = this.legBox.getCenter();
            this.footPart.setPos(fc.x, fc.y, fc.z);
            this.legPart.setPos(lc.x, lc.y, lc.z);
            this.footPart.setPartBoundingBox(this.footBox);
            this.legPart.setPartBoundingBox(this.legBox);
         }
      }
   }
}
