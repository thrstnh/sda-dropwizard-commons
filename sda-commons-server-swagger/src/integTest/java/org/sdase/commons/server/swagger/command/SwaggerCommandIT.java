package org.sdase.commons.server.swagger.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Test;
import org.sdase.commons.server.swagger.SwaggerBundleTestApp;
import org.sdase.commons.server.swagger.test.SwaggerAssertions;
import org.yaml.snakeyaml.Yaml;

public class SwaggerCommandIT {
   private static final String TARGET_FOLDER = "build";

   private final SwaggerBundleTestApp application = new SwaggerBundleTestApp();
   private final Bootstrap<Configuration> bootstrap = new Bootstrap<>(application);
   private final Namespace namespace = mock(Namespace.class);

   private SwaggerCommand command;

   @Before
   public void setUp() {
      application.initialize(bootstrap);

      command = new SwaggerCommand(application.getSwaggerBundle());
   }

   @Test
   public void shouldGenerateSwaggerFiles() throws Exception { // NOSONAR
      // remove old files
      Files.delete(new File(TARGET_FOLDER, "swagger.yaml"));
      Files.delete(new File(TARGET_FOLDER, "swagger.json"));

      when(namespace.getString("targetFolder")).thenReturn(TARGET_FOLDER);

      command.run(bootstrap, namespace);

      // check yaml
      try (FileInputStream fis = new FileInputStream(new File(TARGET_FOLDER, "swagger.yaml"))) {
         new Yaml().load(fis);
      }

      // check json
      SwaggerAssertions.assertValidSwagger2Json(new File(TARGET_FOLDER, "swagger.json"));
   }

   @Test(expected = ArgumentParserException.class)
   public void shouldThrowExceptionOnFolderNotExists() throws Exception { // NOSONAR
      when(namespace.getString("targetFolder")).thenReturn("this-folder-does-not-exist");

      command.run(bootstrap, namespace);
   }
}
