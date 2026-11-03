package cn.bfops;
import java.util.*;
import java.util.regex.*;

/** JEI-style @source terms, combined with ordinary name/ID terms using AND. */
final class LibrarySearch {
    /** Compiled once per distinct query instead of once per candidate row. */
    private static String compiledQuery;
    private static Pattern compiled;

    private static Pattern pattern(String query) {
        if (compiled == null || !query.equals(compiledQuery)) {
            compiledQuery = query;
            compiled = Pattern.compile("(@?)(?:\"([^\"]*)\"|(\\S+))");
        }
        return compiled;
    }

    static boolean matches(String query,String id,String name,String source){
        String normal=(id+" "+name).toLowerCase(Locale.ROOT);
        String origin=source.toLowerCase(Locale.ROOT);
        String normalised=query.trim().toLowerCase(Locale.ROOT);
        Matcher words=pattern(normalised).matcher(normalised);
        while(words.find()){
            String term=words.group(2)!=null?words.group(2):words.group(3);
            if(!(words.group(1).equals("@")?origin:normal).contains(term))return false;
        }
        return true;
    }
}
