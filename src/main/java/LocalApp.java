//import edu.stanford.nlp.ling.CoreAnnotations;
//import edu.stanford.nlp.ling.CoreLabel;
//import edu.stanford.nlp.parser.Parser;
//import edu.stanford.nlp.pipeline.Annotation;
////import edu.stanford.nlp.pipeline.CoreDocument;
////import edu.stanford.nlp.pipeline.ParserAnnotator;
////import edu.stanford.nlp.pipeline.StanfordCoreNLP;
//import edu.stanford.nlp.util.CoreMap;
//
//
//import java.util.List;
//import java.util.Properties;
//
//
//public class LocalApp {
//
//    public static void main(String[] args) {
//
//        // set up pipeline properties
//        Properties props = new Properties();
//        // set the list of annotators to run
//        props.setProperty("annotators", "tokenize,ssplit,pos");
//        // build pipeline
//        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
//        // create a document object
//        CoreDocument document = pipeline.processToCoreDocument(text);
//        // display tokens
//        for (CoreLabel tok : document.tokens()) {
//            System.out.println(String.format("%s\t%s", tok.word(), tok.tag()));
//        }
//
//    }
//
//}
