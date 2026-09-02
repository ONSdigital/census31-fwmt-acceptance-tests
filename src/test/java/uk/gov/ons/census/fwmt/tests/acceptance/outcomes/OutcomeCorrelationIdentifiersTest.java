package uk.gov.ons.census.fwmt.tests.acceptance.outcomes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OutcomeCorrelationIdentifiersTest {

  private static final String SHARED_CASE_ID = "bd6345af-d706-43d3-a13b-8c549e081a76";
  private static final String SHARED_TRANSACTION_ID = "b1646499-c5d8-4fbe-bb21-8e057601a3c2";

  @Test
  void outcomeTemplatesShouldUsePlaceholdersForCorrelationIds() throws Exception {
    List<Path> templateFiles = Stream.concat(
            listTemplateFiles("files/outcome/tm"),
            listTemplateFiles("files/outcome/rm"))
        .collect(Collectors.toList());

    List<String> offenders = templateFiles.stream()
        .filter(this::containsSharedIdentifiers)
        .map(Path::getFileName)
        .map(Path::toString)
        .sorted()
        .collect(Collectors.toList());

    assertThat(offenders)
        .as("outcome templates should not hard-code shared case or transaction identifiers")
        .isEmpty();
  }

  private Stream<Path> listTemplateFiles(String resourceDirectory) throws URISyntaxException, IOException {
    URL directoryUrl = Thread.currentThread().getContextClassLoader().getResource(resourceDirectory);
    assertThat(directoryUrl).as("resource directory %s", resourceDirectory).isNotNull();
    Path directory = Path.of(directoryUrl.toURI());
    return Files.list(directory)
        .filter(path -> path.getFileName().toString().endsWith(".ftl"));
  }

  private boolean containsSharedIdentifiers(Path templateFile) {
    try {
      String content = Files.readString(templateFile, StandardCharsets.UTF_8);
      return content.contains(SHARED_CASE_ID) || content.contains(SHARED_TRANSACTION_ID);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read template file " + templateFile, e);
    }
  }
}