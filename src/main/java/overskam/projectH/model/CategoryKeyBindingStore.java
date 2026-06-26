package overskam.projectH.model;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class CategoryKeyBindingStore {

    private final Path bindingsFile;

    public CategoryKeyBindingStore() {
        this.bindingsFile = Path.of(
                System.getProperty("user.home"),
                ".medical-video-annotator",
                "category-key-bindings.properties"
        );
    }

    public Map<String, String> loadBindings() {
        Map<String, String> bindings = new LinkedHashMap<>();

        if (!Files.exists(bindingsFile)) {
            return bindings;
        }

        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(bindingsFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
            return bindings;
        }

        for (String keyName : properties.stringPropertyNames()) {
            String categoryName = properties.getProperty(keyName);

            if (keyName != null && categoryName != null && !keyName.isBlank() && !categoryName.isBlank()) {
                bindings.put(keyName, categoryName);
            }
        }

        return bindings;
    }

    public void saveBindings(Map<String, String> bindings) throws IOException {
        Files.createDirectories(bindingsFile.getParent());

        Properties properties = new Properties();

        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }

        try (Writer writer = Files.newBufferedWriter(bindingsFile, StandardCharsets.UTF_8)) {
            properties.store(writer, "Medical Video Annotator category key bindings");
        }
    }
}