import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Component
public class GitService {
    private static final String CONFIG_REPOSITORY_URL = "git@example.git.host:user/repository.git";
    private static final String RELATIVE_LOCAL_GIT_FOLDER = "/repositoryFolder";

    private boolean isWindows;

    public GitService() {
        this.isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }

    public void checkoutRepositoryDirectory(String repoWithConfigsDirectoryPath) throws IOException, InterruptedException {
        Path directory = Paths.get(System.getProperty("user.dir") + RELATIVE_LOCAL_GIT_FOLDER);
        gitNoCheckoutClone(directory, CONFIG_REPOSITORY_URL);
        gitSparseCheckoutInit(directory);
        gitSparseCheckoutSet(directory, repoWithConfigsDirectoryPath);
        gitCheckout(directory);
    }

    public void pushFile(String filePath) throws IOException, InterruptedException {
        Path directory = Paths.get(System.getProperty("user.dir") + RELATIVE_LOCAL_GIT_FOLDER);
        gitNoCheckoutClone(directory, CONFIG_REPOSITORY_URL);
        //sparse checkout allows you to checkout only a part of a repository in case it contains too many files
        gitSparseCheckoutInit(directory);
        gitSparseCheckoutSet(directory, filePath);
        gitCommit(directory, "Added/updated " + filePath + " file.");
    }

    private static void gitSparseCheckoutInit(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "sparse-checkout", "init");
    }

    private static void gitAddSparseCheckoutConfig(Path directory, String configPath) throws IOException, InterruptedException {
        runCommand(directory, "git", "sparse-checkout", configPath);
    }

    private static void gitSparseCheckoutSet(Path directory, String sparseCheckoutPattern) throws IOException, InterruptedException {
        runCommand(directory, "git", "add", sparseCheckoutPattern);
    }

    private static void gitStage(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "add", "-A");
    }

    private static void gitCheckout(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "checkout");
    }

    private static void gitCommit(Path directory, String message) throws IOException, InterruptedException {
        runCommand(directory, "git", "commit", "-m", message);
    }

    private static void gitPush(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "push");
    }

    private static void gitNoCheckoutClone(Path directory, String originUrl) throws IOException, InterruptedException {
        runCommand(directory.getParent(), "git", "clone", originUrl, "--no-checkout", directory.getFileName().toString());
    }

    private static void runCommand(Path directory, String... command) throws IOException, InterruptedException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.exists(directory)) {
            throw new RuntimeException("can't run command in non-existing directory '" + directory + "'");
        }
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(command)
                .directory(directory.toFile());
        Process process = processBuilder.start();
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
        outputGobbler.start();
        errorGobbler.start();
        int exit = process.waitFor();
        errorGobbler.join();
        outputGobbler.join();
        if (exit != 0) {
            throw new AssertionError(String.format("runCommand returned %d", exit));
        }
    }

    private static class StreamGobbler extends Thread {

        private final InputStream inputStream;
        private final String type;

        private StreamGobbler(InputStream inputStream, String type) {
            this.inputStream = inputStream;
            this.type = type;
        }

        @Override
        public void run() {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println(type + "> " + line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}