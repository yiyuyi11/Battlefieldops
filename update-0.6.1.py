from pathlib import Path
root=Path('src/main/java/cn/bfops')
p=root/'Network.java';s=p.read_text('utf-8');s=s.replace('e.registrar("2")','e.registrar("2").optional()');s=s.replace('public static void send(ServerPlayer p,Object state){','public static boolean supported(ServerPlayer p){return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(p.connection,State.TYPE.id()) && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(p.connection,Request.TYPE.id());}\n    public static void send(ServerPlayer p,Object state){if(!supported(p))return;');p.write_text(s,'utf-8')
p=root/'Engine.java';s=p.read_text('utf-8');s=s.replace('public boolean killCredited;','public boolean killCredited;\n        public long reviveHeldAt = -100;')
s=s.replace('public void request(ServerPlayer player, String json) {','public void request(ServerPlayer player, String json) {\n        if (!Network.supported(player)) return;')
s=s.replace('case "mark" -> mark(player, request);','case "mark" -> mark(player, request);\n                case "revive" -> {\n                    String target = str(request,"target", "");\n                    ServerPlayer patient = target.isEmpty() ? null : server.getPlayerList().getPlayer(UUID.fromString(target));\n                    if (patient != null) reviveStart(player, patient);\n                }')
s=s.replace('private void join(ServerPlayer player, String id) throws IOException {','private void join(ServerPlayer player, String id) throws IOException {\n        Model.require(Network.supported(player), "安装战地风云客户端模组后才能加入对局");')
s=s.replace('public void state(ServerPlayer player, String selected, boolean open, String message) {','public void state(ServerPlayer player, String selected, boolean open, String message) {\n        if (!Network.supported(player)) {\n            if (open) player.sendSystemMessage(Component.literal("你可以正常游玩服务器；安装战地风云客户端模组后才能加入地图对局。"));\n            return;\n        }')
s=s.replace('if (member != null) {\n            player.stopRiding();','if (member != null) {\n            downPose(player, false);\n            clearReviver(player.getUUID());\n            player.stopRiding();')
s=s.replace('member.alive = true;','member.alive = true;\n        member.downed = false;\n        downPose(player, false);')
s=s.replace('if (other.distanceToSqr(patient) > 3.5 * 3.5) continue;','if (ticks - otherMember.reviveHeldAt > 6 || !canRescue(other, patient)) { otherMember.reviving = null; continue; }')
s=s.replace('downPose(victim, true);\n        // A knockdown','// A knockdown')
s=s.replace('creditKill(victim, attacker);','creditKill(victim, attacker);\n        downPose(victim, true);')
a=s.index('    private static void downPose(');b=s.index('\n    /**',a+10)
s=s[:a]+'''    private static final ResourceLocation CRAWL_SPEED = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "downed_crawl");
    private static void downPose(ServerPlayer player, boolean downed) {
        if (player == null) return;
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (downed && !speed.hasModifier(CRAWL_SPEED)) speed.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(CRAWL_SPEED, -0.75,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (!downed) speed.removeModifier(CRAWL_SPEED);
        }
        player.setPose(downed ? net.minecraft.world.entity.Pose.SWIMMING : net.minecraft.world.entity.Pose.STANDING);
        if (downed) player.setSprinting(false);
    }

    private static boolean canRescue(ServerPlayer helper, ServerPlayer patient) {
        if (!helper.getMainHandItem().isEmpty() || helper.level() != patient.level()
                || helper.distanceToSqr(patient) > 3.5 * 3.5 || !helper.hasLineOfSight(patient)) return false;
        Vec3 eye = helper.getEyePosition();
        return patient.getBoundingBox().inflate(0.15).clip(eye, eye.add(helper.getLookAngle().scale(3.5))).isPresent();
    }
''' + s[b:]
s=s.replace('if (!helper.getMainHandItem().isEmpty()) return false;\n        if (helper.distanceToSqr(target) > 3.5 * 3.5) return false;','if (!canRescue(helper, target)) return false;')
s=s.replace('helperMember.reviving = target.getUUID();','helperMember.reviving = target.getUUID();\n        helperMember.reviveHeldAt = ticks;')
s=s.replace('§a，保持靠近…','§a，空手对准并按住右键…')
s=s.replace('member.alive = false;\n        member.deaths++;','member.alive = false;\n        member.downed = false;\n        downPose(player, false);\n        clearReviver(player.getUUID());\n        member.deaths++;')
s=s.replace('if (helperMember == null || helperMember.team == member.team) continue;','if (helperMember == null || helperMember.team == member.team || !helperMember.map.equals(member.map)) continue;')
# Ensure ordinary award also has red skull when the finishing hit is a headshot.
s=s.replace('award(killer, Awards.streakLabel(count), killPoints, member.name, "kill");','')
s=s.replace('if (headshot) {\n                credited.score', 'award(killer, Awards.streakLabel(count), killPoints, member.name, headshot ? "headshot" : "kill");\n            if (headshot) {\n                credited.score')
p.write_text(s,'utf-8')
p=root/'Client.java';s=p.read_text('utf-8');needle='Minecraft mc = Minecraft.getInstance();\n            if (mc.player != null && OPEN.consumeClick())'
s=s.replace(needle,'''Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                JsonObject me = state.has("me") ? state.getAsJsonObject("me") : null;
                if (me != null && me.has("downed") && me.get("downed").getAsBoolean()) {
                    mc.player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                    mc.player.setSprinting(false);
                }
                if (mc.screen == null && mc.options.keyUse.isDown() && mc.player.getMainHandItem().isEmpty()
                        && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                        && hit.getEntity() instanceof net.minecraft.world.entity.player.Player target
                        && mc.player.tickCount % 4 == 0) {
                    JsonObject request = request("revive", Engine.str(state,"selected",""));
                    request.addProperty("target", target.getUUID().toString());
                    send(request);
                }
            }
            if (mc.player != null && OPEN.consumeClick())''')
s=s.replace('FeedIcons.draw(graphics, popup.icon(), anchorX, y, iconColor);','graphics.fill(anchorX - 2, y - 2, anchorX + FeedIcons.width(popup.icon()) + 2, y + FeedIcons.height(popup.icon()) + 2, withAlpha(0xff080d12, alpha));\n                FeedIcons.draw(graphics, popup.icon(), anchorX, y, iconColor);')
p.write_text(s,'utf-8')
p=root/'BattlefieldOps.java';s=p.read_text('utf-8');s=s.replace('if(engine!=null&&e.getTarget() instanceof ServerPlayer target','if(e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&engine!=null&&e.getTarget() instanceof ServerPlayer target');p.write_text(s,'utf-8')
p=root/'CombatHud.java';s=p.read_text('utf-8');s=s.replace('the player is blind and cannot fight, so the one thing they need is the clock.', 'show the bleed-out clock without obscuring crawling or aiming.');s=s.replace('出血倒计时 "+left+"s · 等待队友救援','出血倒计时 "+left+"s · 队友空手按住右键救援');p.write_text(s,'utf-8')
for f in ['build.py','src/main/resources/META-INF/neoforge.mods.toml']:
 p=Path(f);p.write_text(p.read_text('utf-8').replace('0.6.0','0.6.1'),'utf-8')
