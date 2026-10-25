package cn.bfops;
import java.util.*;
import java.util.regex.*;

/** JEI-style @source terms, combined with ordinary name/ID terms using AND. */
final class LibrarySearch {
    static boolean matches(String query,String id,String name,String source){
        String normal=(id+" "+name).toLowerCase(Locale.ROOT);
        String origin=source.toLowerCase(Locale.ROOT);
        Matcher words=Pattern.compile("(@?)(?:\"([^\"]*)\"|(\\S+))").matcher(query.trim().toLowerCase(Locale.ROOT));
        while(words.find()){
            String term=words.group(2)!=null?words.group(2):words.group(3);
            if(!(words.group(1).equals("@")?origin:normal).contains(term))return false;
        }
        return true;
    }
}
