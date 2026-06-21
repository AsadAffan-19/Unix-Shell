import java.util.Scanner;
import java.io.File;

public class Main {
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
                    String path = System.getenv("PATH");
                    // System.out.println(path); // add here

                    String[] dirs = path.split(":");

                    boolean found = false;

                    for (String dir : dirs) {
                        File file = new File(dir, command);

                        boolean exist = file.exists();
                        boolean executable = file.canExecute();

                        if (exist && executable) {
                            System.out.println(command + " is " + dir);
                            found = true;
                            break;

                        }

                    }

                    if (!found) {
                        System.out.println(command + ": not found");
                    }

                }
            } else {
                System.out.println(input + ": command not found");

            }

        }
    }
}
