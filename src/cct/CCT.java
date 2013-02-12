/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cct;

import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
/**
 * Convert CCT documents to XML for import to HRIT
 * @author desmond
 */
public class CCT 
{
    static boolean preservingLineBreaks = true;
    File src;
    File dst;
    String linePending;
    String paraPending;
    String textPending;
    HashMap<String,String> textCommands;
    HashMap<String,String> lineCommands;
    HashMap<String,String> paraCommands;
    HashMap<String,String> ligatures;
    HashMap<String,Map<Character,Character>> accentCommands;
    CCT( String srcFolder )
    {
        src = new File( srcFolder );
        dst = new File( srcFolder+"-xml" );
        if ( !dst.exists() )
            dst.mkdir();
        textCommands = new HashMap<String,String>();
        lineCommands = new HashMap<String,String>();
        paraCommands = new HashMap<String,String>();
        accentCommands = new HashMap<String,Map<Character,Character>>();
        textCommands.put(" -- "," – ");
        lineCommands.put("ht", "<head>");
        textCommands.put("op\"","“");
        textCommands.put("op'","‘");
        textCommands.put("oi","<hi rend=\"initial\">");
        paraCommands.put("cop", "<p>");
        lineCommands.put("au", "<head type=\"author\">");
        lineCommands.put("ct", "<head type=\"author\">");
        lineCommands.put("ti", "<head type=\"title\">");
        lineCommands.put("pt", "<head type=\"part title\">");
        textCommands.put("stx","<lb/>");
        textCommands.put("ex","<q>");
        textCommands.put("/ex","</q>");
        lineCommands.put("pn","<head type=\"part number\">");
        lineCommands.put("cn","<head>");
        textCommands.put("---","—");
        textCommands.put("it","<hi rend=\"italic\">");
        textCommands.put("sc","<hi rend=\"smallcaps\">");
        textCommands.put("ro","</hi>");
        HashMap<Character,Character> acutes = new HashMap<Character,Character>();
        acutes.put('e','é');
        acutes.put('a','á');
        acutes.put('i','í');
        accentCommands.put("a",acutes);
        HashMap<Character,Character> graves = new HashMap<Character,Character>();
        graves.put('a','à');
        graves.put('e','è');
        accentCommands.put("gr",graves);
        accentCommands.put("g",graves);
        HashMap<Character,Character> umlauts = new HashMap<Character,Character>();
        umlauts.put('u','ü');
        umlauts.put('o','ö');
        umlauts.put('a','ä');
        umlauts.put('i','ï');
        accentCommands.put("um",umlauts);
        ligatures = new HashMap<String,String>();
        ligatures.put("oe","œ");
        ligatures.put("ae","æ");
    }
    /**
     * Swap endings from .txt or whatever to .xml
     * @param textFile the raw textfile without a suffix or the wrong one
     * @return an xml file name
     */
    String xmlise( String textFile )
    {
        int pos = textFile.indexOf(".");
        if ( pos == -1 )
            return textFile+".xml";
        else
            return textFile.substring(0,pos)+".xml";
    }
    /**
     * Convert all the files in a directory 
     * @param from the directory to convert from
     * @param to the directory to convert to
     * @return true if it worked
     */
    void convert( File from, File to ) throws Exception
    {
        File[] contents = from.listFiles();
        for ( int i=0;i<contents.length;i++ )
        {
            if ( contents[i].isDirectory() )
            {
                File destDir = new File( to, contents[i].getName() );
                if ( !destDir.exists() )
                    destDir.mkdir();
                convert( contents[i], destDir );
            }
            else // it's a file
            {
                File destFile = new File( to, xmlise(contents[i].getName()) );
                convertFile( contents[i], destFile );
            }
        }
    }
    void writeLineEnd( FileOutputStream fos ) throws Exception
    {
        if ( linePending != null )
        {
            fos.write( linePending.getBytes("UTF-8") );
            linePending = null;
        }
    }
    void writeParaEnd( FileOutputStream fos ) throws Exception
    {
        if ( paraPending != null )
        {
            if ( linePending != null )
                linePending = null;
            fos.write( paraPending.getBytes("UTF-8") );
            paraPending = null;
        }
    }
    void writeParaStart( FileOutputStream fos ) throws Exception 
    {
        fos.write( "<p>".getBytes() );
        paraPending = "</p>\n";
    }
    /**
     * Read a single dot command and write it to the output
     * @param line the line containing the command
     * @param fos the output stream of the destination file
     * @return true if it worked
     * @throws Exception 
     */
    void convertDotCommand( String line, FileOutputStream fos ) throws Exception
    {
        if ( linePending != null )
            writeLineEnd( fos );
        if ( line.length()>1&& line.charAt(1)=='p' )
        {
            String pn = line.substring(2);
            String command = "<pb n=\""+pn.trim()+"\"/>\n";
            fos.write( command.getBytes() );
        }
        else
        {
            System.out.println("unknown dot command "+line);
        }
    }
    /**
     * Get the end tag for a given start tag maybe with attributes
     * @param startTag the start tag
     * @return its corresponding end-tag
     */
    String endTag( String startTag )
    {
        StringBuilder sb = new StringBuilder();
        for ( int i=0;i<startTag.length();i++ )
        {
            char token = startTag.charAt(i);
            if ( token == '<' )
                sb.append("</");
            else if ( token == ' ' )
            {
                sb.append(">");
                break;
            }
            else
                sb.append( token );
        }
        return sb.toString();
    }
    String escape( String input )
    {
        StringBuilder sb = new StringBuilder( input );
        for ( int i=0;i<sb.length();i++ )
        {
            char token = sb.charAt(i);
            if ( token == 34 )
                sb.setCharAt(i,'”');
            else if ( token == 39 )
                sb.setCharAt(i,'’');
        }
        return sb.toString();
    }
    /**
     * Write body text to the output
     * @param fos the file output
     * @param text the text string to write
     * @throws Exception 
     */
    void writeData( FileOutputStream fos, String text ) throws Exception
    {
        if ( textPending != null )
        {
            fos.write( escape(textPending).getBytes("UTF-8") );
            textPending = null;
        }
        fos.write( text.getBytes("UTF-8") );
    }
    /**
     * Write body text to the output
     * @param fos the file output
     * @param text the text string to write
     * @throws Exception 
     */
    void writeText( FileOutputStream fos, String text ) throws Exception
    {
        if ( textPending != null )
        {
            fos.write( escape(textPending).getBytes("UTF-8") );
            textPending = null;
        }
        fos.write( escape(text).getBytes("UTF-8") );
    }
    /**
     * Write the contents of a line
     * @param line the current line
     * @param fos the output stream
     * @return true if it worked
     */
    boolean writeLineContents( String line, FileOutputStream fos ) throws Exception
    {
        boolean result = true;
        String trimmed = line.trim();
        StringTokenizer st = new StringTokenizer( trimmed, "{}", true );
        int state = 0;
        while ( st.hasMoreTokens() )
        {
            String token = st.nextToken();
            switch ( state )
            {
                case 0: // looking for '{'
                    if ( token.equals("{") )
                        state = 1;
                    else
                        textPending = token;
                    break;
                case 1: // reading command
                    if ( accentCommands.containsKey(token) )
                    {
                        Map<Character,Character> map = accentCommands.get(token);
                        if ( textPending != null && textPending.length()>0 )
                        {
                            char last = textPending.charAt(textPending.length()-1);
                            if ( map.containsKey(last) )
                            {
                                String adjusted =  textPending.substring(
                                    0,textPending.length()-1)+map.get(last);
                                textPending = null;
                                writeText( fos, adjusted );
                            }
                            else
                                System.out.println("ignored accent "+token
                                    +" does not match preceding "+last);
                        }
                        else
                            System.out.println("accent "+token
                                +" not preceded by text");
                    }
                    else if ( token.equals("lig") )
                    {
                        if ( textPending != null )
                        {
                            Set<String>keys = ligatures.keySet();
                            Iterator<String> iter = keys.iterator();
                            while ( iter.hasNext() )
                            {
                                String lig = iter.next();
                                if ( textPending.length()>= lig.length() )
                                {
                                    int end = textPending.length()-lig.length();
                                    String match = textPending.substring(end);
                                    if ( match.equals(lig) )
                                    {
                                        String rep = 
                                            textPending.substring(0,end)
                                                +ligatures.get(lig);
                                        textPending = null;
                                        writeText( fos, rep );
                                        break;
                                    }
                                }
                            }
                            if ( !iter.hasNext() )
                            {
                                System.out.println("failed to match ligature");
                                if ( textPending != null )
                                {
                                    String copyOf = textPending;
                                    textPending = null;
                                    writeText( fos, copyOf );
                                }
                            }
                        }
                    }
                    else if ( textCommands.containsKey(token) )
                    {
                        writeData( fos,textCommands.get(token) );
                    }
                    else if ( lineCommands.containsKey(token) )
                    {
                        String command = lineCommands.get(token);
                        writeData( fos,command );
                        linePending = endTag( command )+"\n";
                    }
                    else if ( paraCommands.containsKey(token) )
                    {
                        String command = paraCommands.get( token );
                        writeData( fos, command );
                        paraPending = endTag( command+"\n" );
                    }
                    else
                    {
                        System.out.println("ignoring unknown command "+token);
                    }
                    state = 2;
                    break;
                case 2: // reading closing brace
                    if ( !token.equals("}") )
                    {
                        //System.out.println(line);
                        System.out.println("missing closing brace");
                    }
                    state = 0;
                    break;
            }
        }
        if ( textPending != null )
        {
            String copyOf = textPending;
            textPending = null;
            writeText( fos, copyOf );
        }
        if ( linePending == null )
        {
            if ( CCT.preservingLineBreaks )
                linePending = "<lb/>\n";
            else
                linePending = "\n";
        }
        return result;
    }
    /**
     * Convert a single file to XML
     * @param src the src file
     * @param dst the dst file
     * @return true if it worked
     */
    void convertFile( File src, File dst ) throws Exception
    {
        try
        {
            //System.out.println( src.getName() );
            FileOutputStream fos = new FileOutputStream(dst);
            FileReader fr = new FileReader( src );
            BufferedReader br = new BufferedReader( fr );
            fos.write( "<TEI><body><text>\n".getBytes() );
            String line = br.readLine();
            do 
            {
                if ( line.startsWith(".")
                    &&line.length()>1
                    &&Character.isLetter(line.charAt(1)) )
                    convertDotCommand(line,fos);
                else if ( line.startsWith(" ") )
                {
                    writeParaEnd( fos );
                    writeLineEnd( fos );
                    writeParaStart( fos );
                    writeLineContents( line, fos );
                }
                else
                {
                    if ( linePending != null )
                        writeLineEnd( fos );
                    writeLineContents( line, fos );
                }
                line = br.readLine();
            } while ( line != null );
            writeParaEnd( fos );
            writeLineEnd( fos );
            fos.write( "</text></body></TEI>".getBytes() );
            fos.close();
        }
        catch ( Exception e )
        {
            e.printStackTrace( System.out );
        }
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        try
        {
            if ( args.length==1 )
            {
                CCT cct = new CCT( args[0] );
                cct.convert(cct.src,cct.dst);
            }
            else
                System.out.println("usage: java CCT <folder>");
        }
        catch ( Exception e )
        {
        }
    }
}
