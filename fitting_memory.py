from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Client.java';s=f.read_text('utf-8');marker='    static JsonObject state = new JsonObject(), catalog = new JsonObject();';assert marker in s;s=s.replace(marker,marker+'\n    static final java.util.Map<String,java.util.Map<String,String>> fittingMemory=new java.util.LinkedHashMap<>();');s=s.replace('            state = new JsonObject();','            fittingMemory.clear();\n            state = new JsonObject();');f.write_text(s,'utf-8')
f=p/'BattleScreen.java';s=f.read_text('utf-8');s=s.replace('    private final Map<String,Map<String,String>> fittings=new HashMap<>();\n    private Map<String,String> fitting(String kind,String id){return fittings.computeIfAbsent(kind+":"+id,k->new LinkedHashMap<>());}','''    private Map<String,String> fitting(String kind,String id){
        if(Client.fittingMemory.size()>512)Client.fittingMemory.clear();
        return Client.fittingMemory.computeIfAbsent(selected+"/"+kind+":"+id,k->new LinkedHashMap<>());
    }''');f.write_text(s,'utf-8')
