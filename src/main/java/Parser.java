import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

public class Parser {

    public static void main(String[] args){
//        String englishPCFG_path = "D:\\Users\\rulid\\stanford parser\\stanford-parser-full-2020-11-17\\englishPCFG.ser.gz";
        String englishPCFG_path = "englishPCFG.ser.gz";
        System.out.println("out_" + System.currentTimeMillis());

    }

//    parsing_method = wordsAndTags (=POS) or penn (=CONSTITUENCY) or typedDependencies (=DEPENDENCY)
    public static String parse_into_file(String parsing_method, String output_file_path, String input_file_path){
        String englishPCFG_path = "englishPCFG.ser.gz";
        try {
            LexicalizedParser lexicalizedParser = LexicalizedParser.loadModel(englishPCFG_path);
            File input_file = new File(input_file_path);
            Scanner myReader = new Scanner(input_file);
            PrintWriter pw = null;
            File f=new File(output_file_path);
            pw=new PrintWriter(f);
            String text_line = "";
            Tree parseTree = null;
            TreePrint treePrint = new TreePrint(parsing_method);

            while (myReader.hasNextLine()) {
                text_line = myReader.nextLine();
                parseTree = lexicalizedParser.parse(text_line);
                treePrint.printTree(parseTree, pw);
            }
            myReader.close();
        } catch (Exception e) {
            System.out.println("An error occurred.");
            return "exception: " + e.getMessage();
        }

        return "success";
    }
}
