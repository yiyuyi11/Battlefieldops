from pathlib import Path
root=Path(__file__).parent/'src/main/java/cn/bfops'
p=root/'Engine.java';s=p.read_text('utf-8')
a=s.index('        Path dest = folder.resolve("maps")',s.index('    private void save('));b=s.index('\n    }',a)
s=s[:a]+'        MapFiles.save(folder.resolve("maps"),map.id,BattlefieldOps.GSON.toJson(map));'+s[b:]
s=s.replace('                    state(player, arena.id, false, "地图已保存");','''                    Network.send(player,Map.of("type","saved","arena",arena));
                    state(player, arena.id, false, "地图已保存");''')
p.write_text(s,'utf-8')
p=root/'Client.java';s=p.read_text('utf-8')
needle='            state = payload;'
s=s.replace(needle,'''            if (Engine.str(payload,"type","").equals("saved")) {
                if(mc.screen instanceof OpsScreen screen)screen.saved(payload.getAsJsonObject("arena"));
                return;
            }
'''+needle,1)
p.write_text(s,'utf-8')
p=root/'OpsScreen.java';s=p.read_text('utf-8')
s=s.replace('    private int refreshTicks;','''    private int refreshTicks;
    private int formTab=-1;
    private final Map<String,String> rawFields=new HashMap<>();
    private final DraftHistory history=new DraftHistory();
    private String savedDraft="";
    private String savingId="";
    private long saveStarted;
    private int movingVertex=-1;
    private String jsonDraft(){return BattlefieldOps.GSON.toJson(draft);}
    private boolean dirty(){return !invalid.isEmpty()||!jsonDraft().equals(savedDraft);}
    private String fieldKey(String label){return tab+":"+pointIndex+":"+spawnIndex+":"+kitIndex+":"+label;}
    void saved(JsonObject arena){
        if(!Engine.str(arena,"id","").equals(savingId))return;
        savedDraft=BattlefieldOps.GSON.toJson(BattlefieldOps.GSON.fromJson(arena,Model.Arena.class));
        savingId="";
        ok(dirty()?"已保存提交的版本；还有后续修改未保存":"地图已写入服务器（已保留上一版备份）");
        rebuild();
    }
    private void discardThen(Runnable action){
        if(!dirty()){action.run();return;}
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
            minecraft.setScreen(this);
            if(yes)action.run();
        },net.minecraft.network.chat.Component.literal("还有未保存的地图修改"),
          net.minecraft.network.chat.Component.literal("继续会丢弃当前草稿。可取消后按 Ctrl+S 保存。")));
    }
    @Override public void onClose(){discardThen(()->minecraft.setScreen(null));}
    private void undo(boolean redo){
        history.record(jsonDraft());String value=redo?history.redo():history.undo();
        if(value!=null){draft=BattlefieldOps.GSON.fromJson(value,Model.Arena.class);rawFields.clear();invalid.clear();rebuild();}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_S){guard(this::save);return true;}
        if(!(getFocused() instanceof EditBox)&&hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z){undo(hasShiftDown());return true;}
        if(!(getFocused() instanceof EditBox)&&hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y){undo(true);return true;}
        return super.keyPressed(key,scan,modifiers);
    }''')
s=s.replace('            if (msg.startsWith("操作失败")) fail(msg);','            if (msg.startsWith("操作失败")) {savingId="";fail(msg);}')
s=s.replace('        if (draft == null) fresh();\n        center();','''        if (draft == null) fresh();
        else {savedDraft=jsonDraft();rawFields.clear();invalid.clear();history.reset(jsonDraft());}
        center();''')
s=s.replace('        draft = new Model.Arena();','''        rawFields.clear();invalid.clear();savedDraft="";
        draft = new Model.Arena();''',1)
s=s.replace('        pointIndex = spawnIndex = kitIndex = 0;','        pointIndex = spawnIndex = kitIndex = 0;\n        history.reset(jsonDraft());',1)
s=s.replace(' + " · " + dimensionName();',' + " · " + dimensionName() + (dirty()?" · 未保存":" · 已保存");')
s=s.replace('    protected void poll() {','''    protected void poll() {
        if(!savingId.isEmpty()&&System.currentTimeMillis()-saveStarted>10000){savingId="";warn("未收到保存回执，请重试或检查连接；草稿仍保留");}
''')
s=s.replace('    protected void rebuild() {\n        clearWidgets();','''    protected void rebuild() {
        if(form!=null&&formTab>=0)scrollMemory.put(formTab,form.scroll());
        clearWidgets();''')
s=s.replace('        form = newForm(Ui.headerBody(layout.left));','        form = newForm(Ui.headerBody(layout.left));\n        formTab=tab;')
s=s.replace('        revalidate();\n        scrollMemory.put','        revalidate();\n        history.record(jsonDraft());\n        scrollMemory.put')
s=s.replace('    private void cycleMap(int delta) {','    private void cycleMap(int delta) {discardThen(()->cycleMapConfirmed(delta));}\n    private void cycleMapConfirmed(int delta) {')
s=s.replace('        Model.require(invalidate().isEmpty()', '        Model.require(savingId.isEmpty(),"正在保存，请等待服务器回执");\n        Model.require(invalidate().isEmpty()',1)
s=s.replace('        selected = draft.id;\n        Client.send(request);','''        selected = draft.id;
        savingId=draft.id;saveStarted=System.currentTimeMillis();
        say("正在保存到服务器…");
        Client.send(request);''',1)
s=s.replace('Integer.toString(value), 9, text -> {','rawFields.getOrDefault(fieldKey(label),Integer.toString(value)), 9, text -> {\n                    rawFields.put(fieldKey(label),text);')
s=s.replace('String.format(Locale.ROOT, "%.1f", value), 16, text -> {','rawFields.getOrDefault(fieldKey(label),String.format(Locale.ROOT, "%.1f", value)), 16, text -> {\n                    rawFields.put(fieldKey(label),text);')
s=s.replace('                    Model.require(Double.isFinite(parsed), label + " 数值无效");','                    if(!Double.isFinite(parsed)){invalid.add(label);throw new IllegalArgumentException(label+" 数值无效");}')
s=s.replace('"写入服务器", "", "", invalid.isEmpty(), this::save','"Ctrl+S", "", "", true, this::save')
s=s.replace('row(form, "重新载入", "丢弃改动", () -> {\n                pendingLoad = true;\n                action("state");\n            });','row(form, "重新载入", "丢弃改动", () -> discardThen(()->{pendingLoad=true;action("state");}));')
# Fresh actions are the two explicit toolbar/menu entries, not initial empty-world construction.
s=s.replace('row(form, "新建草稿", "以当前位置", () -> {','row(form, "新建草稿", "以当前位置", () -> discardThen(()->{')
s=s.replace('say("新草稿已生成，请调整边界、据点与出生点");\n            });','say("新草稿已生成，请调整边界、据点与出生点");\n            }));')
s=s.replace('place(barForm, cells[columns - 1], "新建", "", "", "", true, () -> {','place(barForm, cells[columns - 1], "新建", "", "", "", true, () -> discardThen(()->{')
start=s.index('place(barForm, cells[columns - 1]');end=s.index('            });',start);s=s[:end]+s[end:].replace('            });','            }));',1)
s=s.replace('            map.zoom(body, mouseX, mouseY, vertical > 0);\n            rebuild();','            if(vertical==0)return false;\n            map.zoom(body, mouseX, mouseY, vertical > 0);')
s=s.replace('        if (form != null && form.wheel(mouseX, mouseY, vertical)) return true;','''        if(sideForm!=null&&sideForm.wheel(mouseX,mouseY,vertical))return true;
        if (form != null && form.wheel(mouseX, mouseY, vertical)) {rememberScroll();return true;}''')
s=s.replace('            double y = map.groundY(body, mouseX, mouseY);','''            double y = map.groundY(body, mouseX, mouseY);
            if(placing!=null&&!Double.isFinite(y)){warn("该区块尚未加载或维度不一致，请靠近后再放置");return true;}''')
# Catalogue previous-server-page must actually request the changed server page.
s=s.replace('                catalogLocal = Integer.MAX_VALUE;\n            }','                catalogLocal = Integer.MAX_VALUE;\n                fetchCatalog();\n            }')
# Boundary drag via Shift; duplicate vertices are refused without disturbing the existing form.
s=s.replace('            if (button == 0 && points.size() < 64) {\n                points.add(new Model.Vec(Math.rint(wx(mouseX)), Math.rint(wz(mouseY))));','''            if(button==0&&hasShiftDown()){
                for(int i=0;i<points.size();i++)if(Math.hypot(map.sx(canvasBody(),points.get(i).x())-mouseX,map.sz(canvasBody(),points.get(i).z())-mouseY)<10){history.record(jsonDraft());movingVertex=i;return true;}
            }
            if (button == 0 && points.size() < 64) {
                Model.Vec vertex=new Model.Vec(Math.rint(wx(mouseX)),Math.rint(wz(mouseY)));
                if(points.contains(vertex)){warn("该顶点已经存在，边界会自动闭合");return true;}
                points.add(vertex);''')
s=s.replace('        if (button == 2 && (inCanvas(mouseX, mouseY)','''        if(button==0&&movingVertex>=0&&inCanvas(mouseX,mouseY)){
            polygon().set(movingVertex,new Model.Vec(Math.rint(wx(mouseX)),Math.rint(wz(mouseY))));return true;
        }
        if (button == 2 && (inCanvas(mouseX, mouseY)''')
idx=s.index('    @Override\n    public boolean mouseScrolled')
s=s[:idx]+'''    @Override public boolean mouseReleased(double x,double y,int button){
        if(movingVertex>=0){movingVertex=-1;rebuild();return true;}
        return super.mouseReleased(x,y,button);
    }

'''+s[idx:]
s=s.replace('左键加点 · 右键撤销 · 中键拖动平移 · 滚轮缩放','左键加点 · Shift拖顶点 · 右键撤销 · 中键平移 · 滚轮缩放')
p.write_text(s,'utf-8')
