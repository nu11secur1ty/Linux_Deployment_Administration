//@nu11secur1ty
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class findbirth {

    public static void main(String[] args) throws IOException {
        
        String fileName = "mysql.sh";
        
        File myfile = new File(fileName);
        Path path = myfile.toPath();
        
        BasicFileAttributes fatr = Files.readAttributes(path, 
                BasicFileAttributes.class);
        
        System.out.printf("File creation time: %s%n", fatr.creationTime());
    }
}
