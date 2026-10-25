from pathlib import Path
p=Path('src/main/java/cn/bfops')
def edit(n,a,b):
 f=p/n;s=f.read_text('utf-8');assert a in s,n+': '+a[:60];f.write_text(s.replace(a,b),'utf-8')
edit('Form.java','int viewportTop = area.y() + 1;\n        int viewportBottom = area.bottom() - 1;','int viewportTop = area.y();\n        int viewportBottom = area.bottom();')
edit('Layout.java','int barHeight = height < 210 ? 0 : 20;','int barHeight = 24;')
edit('Engine.java','(entry.id() + " " + entry.name()).toLowerCase(Locale.ROOT).contains(query)','LibrarySearch.matches(query,entry.id(),entry.name(),LibrarySource.searchable(entry.id(),entry.kind()))')
edit('ChoiceScreen.java','(c.name + " " + c.id).toLowerCase(Locale.ROOT).contains(needle)','LibrarySearch.matches(needle,c.id,c.name,LibrarySource.searchable(c.id,c.kind))')
edit('ChoiceScreen.java','    private int hovered;','    private int hovered;\n    private boolean searchChanged;\n    @Override protected void poll(){if(searchChanged){searchChanged=false;rebuild();}}')
edit('ChoiceScreen.java','            hovered = 0;\n            rebuild();','            hovered = 0;\n            searchChanged = true;')
edit('ChoiceScreen.java','        listForm.add(search);','        search.setHint(net.minecraft.network.chat.Component.literal("名称 / @模组名"));\n        listForm.add(search);')
edit('ChoiceScreen.java','        return prompt;','        return "@模组名 + 名称 · "+filtered().size()+" 项";')
edit('ChoiceScreen.java','choice.id, body.w() - 12)','"@"+LibrarySource.name(choice.id)+" · "+choice.id, body.w() - 12)')
edit('Engine.java','if (!member.alive) continue;','if (!member.alive || !player.isAlive() || member.team < 0) continue;')
edit('Engine.java','Math.hypot(player.getX() - point.x, player.getZ() - point.z) <= point.radius\n                            && Math.abs(player.getY() - point.y) <= point.height / 2','ObjectiveZone.contains(point,player.getX(),player.getY(),player.getZ())')
edit('CombatHud.java','Math.hypot(mc.player.getX()-p.x,mc.player.getZ()-p.z)>p.radius||Math.abs(mc.player.getY()-p.y)>p.height/2','!ObjectiveZone.contains(p,mc.player.getX(),mc.player.getY(),mc.player.getZ())')
edit('Model.java','require(contains(boundary,p.x,p.z),"据点必须在地图边界内");','require(contains(boundary,p.x,p.z),"据点必须在地图边界内");require(p.y>=minY&&p.y<=maxY,"据点 "+p.id+" 的 Y 超出战区高度，请在据点页修正");')
edit('Model.java','require(contains(boundary,s.x,s.z),"出生点必须在地图边界内");','require(contains(boundary,s.x,s.z),"出生点必须在地图边界内");require(s.y>=minY&&s.y<=maxY,"出生点 "+s.id+" 的 Y 超出战区高度");')
edit('Client.java','            state = payload;','''            if(Engine.str(payload,"type","").equals("result")){
                mc.setScreen(new ResultScreen(payload,mc.screen));return;
            }
            state = payload;''')
edit('Engine.java','            leave(player);\n            state(player, id, true, reason);','''            Map<String,Object> summary=new LinkedHashMap<>();
            summary.put("type","result");summary.put("mapName",match.rules.map.name);
            summary.put("reason",reason);summary.put("outcome",match.rules.winner==-2?"对局结束":winner<0?"平局":winner==member.team?"胜利":"失败");
            summary.put("kills",member.kills);summary.put("deaths",member.deaths);
            summary.put("captures",member.captures);summary.put("score",member.score);
            leave(player);
            state(player, id, false, reason);
            Network.send(player,summary);''')
edit('OpsScreen.java','field(form, "搜索武器 / 载具", query, 48','field(form, "搜索：名称 / @模组名", query, 96')
edit('OpsScreen.java','decimal("占领高度", point.height','decimal("总高度（Y 上下各一半）", point.height')
edit('OpsScreen.java','            place(barForm, cells[1], "保存地图", "Ctrl+S", "", "", true, this::save);','''            var saveButton=place(barForm, cells[1], savingId.isEmpty()?"保存地图":"保存中…", "", "", "", savingId.isEmpty(), this::save);
            saveButton.primary=true;
            place(barForm,cells[2],"作战房间","","","",true,()->{
                Model.require(!dirty(),"请先点击保存地图，收到成功回执后再进入作战房间");
                minecraft.setScreen(new BattleScreen());Client.send(Client.request("state",selected));
            });''')
edit('OpsScreen.java','        say("正在保存到服务器…");\n        Client.send(request);','        say("正在保存到服务器…");\n        Client.send(request);\n        rebuild();')
edit('OpsScreen.java','if(!savingId.isEmpty()&&System.currentTimeMillis()-saveStarted>10000){savingId="";warn("未收到保存回执，请重试或检查连接；草稿仍保留");}','if(!savingId.isEmpty()&&System.currentTimeMillis()-saveStarted>10000){savingId="";warn("未收到保存回执，请重试或检查连接；草稿仍保留");rebuild();}')
edit('OpsScreen.java','if (msg.startsWith("操作失败")) {savingId="";fail(msg);}','if (msg.startsWith("操作失败")) {savingId="";fail(msg);rebuild();}')
edit('Client.java','                screen.notice = "配置过大，请减少顶点 / 配装 / 载具数量";','                throw new IllegalArgumentException("配置过大，请减少顶点 / 配装 / 载具数量；未提交保存");')
edit('OpsScreen.java','        Client.send(request);\n        rebuild();','        try{Client.send(request);}catch(RuntimeException ex){savingId="";rebuild();throw ex;}\n        rebuild();')
