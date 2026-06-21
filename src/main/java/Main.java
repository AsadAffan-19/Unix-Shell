import java.util.Scanner;
import java.io.File;

public class Main {

    static String findExecutable(String command) {

        String path = System.getenv("PATH");
        // System.out.println(path); // add here

        String[] dirs = path.split(":");

        for (String dir : dirs) {
            File file = new File(dir, command);

            boolean exist = file.exists();
            boolean executable = file.canExecute();

            if (exist && executable) {
                return file.getAbsolutePath();
            }

        }

        return null;

    }

    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;

            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));

            } else if (input.startsWith("type ")) {
                String command = input.substring(5).trim();

                if (command.equals("echo") || command.equals("exit") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");

                } else {
                    String res = findExecutable(command);

                    System.out.println(res == null ? command + ": not found" : command + " is " + res);

                }

            } else {
                String[] parts = input.split(" ");

                String res = findExecutable(parts[0]);

                if (res != null) {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.inheritIO();
                    Process p = pb.start();
                    p.waitFor();
                } else {
                    System.out.println(parts[0] + ": not found");
                }

            }

        }
    }

}
