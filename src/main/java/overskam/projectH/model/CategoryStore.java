package overskam.projectH.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CategoryStore {

    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "gallbladder",
            "cystic_duct",
            "cystic_artery",
            "liver",
            "instrument"
    );

    private final Path categoriesFile;

    public CategoryStore() {
        this.categoriesFile = Path.of(
                System.getProperty("user.home"),
                ".medical-video-annotator",
                "categories.txt"
        );
    }

    public List<String> loadCategories() {
        Set<String> categories = new LinkedHashSet<>(DEFAULT_CATEGORIES);

        if (Files.exists(categoriesFile)) {
            try {
                for (String line : Files.readAllLines(categoriesFile, StandardCharsets.UTF_8)) {
                    String normalized = normalizeCategoryName(line);

                    if (!normalized.isBlank()) {
                        categories.add(normalized);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>(categories);
    }

    public void saveCategories(List<String> categories) throws IOException {
        Files.createDirectories(categoriesFile.getParent());
        Files.write(categoriesFile, categories, StandardCharsets.UTF_8);
    }

    public String normalizeCategoryName(String categoryName) {
        if (categoryName == null) {
            return "";
        }

        return categoryName.trim()
                .toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "");
    }
}