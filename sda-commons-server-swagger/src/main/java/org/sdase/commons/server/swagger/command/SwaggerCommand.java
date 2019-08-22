package org.sdase.commons.server.swagger.command;

import com.google.common.io.Files;
import io.dropwizard.Configuration;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import java.io.File;
import java.io.IOException;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.sdase.commons.server.swagger.SwaggerBundle;

/**
 * A command that generates the swagger.json and swagger.yaml from the service.
 */
public class SwaggerCommand extends Command {
   private SwaggerBundle swaggerBundle;

   /**
    * Create the 'generate-swagger' command
    *
    * @param swaggerBundle
    *           the instance of the SwaggerBundle that was created
    */
   public SwaggerCommand(SwaggerBundle swaggerBundle) {
      super("generate-swagger", "Generate the swagger yaml file");

      this.swaggerBundle = swaggerBundle;
   }

   @Override
   public void configure(Subparser subparser) {
      subparser.addArgument("targetFolder").help("Target folder where the swagger.{yaml|json} files are created");
   }

   @Override
   public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
      // Create a new environment
      final Environment environment = new Environment(bootstrap.getApplication().getName(), bootstrap.getObjectMapper(),
            bootstrap.getValidatorFactory().getValidator(), bootstrap.getMetricRegistry(), bootstrap.getClassLoader(),
            bootstrap.getHealthCheckRegistry());

      // only initialize the swagger bundle since it does not depend on any
      // other bundle with an empty configuration
      swaggerBundle.run(new Configuration(), environment);

      // export the swagger files
      exportSwagger(namespace.getString("targetFolder"));
   }

   /**
    * Export the swagger files to the folder that is specified by the command
    * arguments
    *
    * @param targetFolder
    *           the target folder to store the swagger files in
    */
   private void exportSwagger(String targetFolder) throws ArgumentParserException, IOException {
      File folder = new File(targetFolder);

      if (!folder.isDirectory()) {
         throw new ArgumentParserException("Folder does not exist", null);
      }

      // call the swagger generation
      Swagger process = swaggerBundle.getSwaggerResource().process(null, null, null, null, null);

      // the swagger file should not contain any base path or url to be environment agnostic
      process.setBasePath(null);

      File jsonFile = new File(folder, "swagger.json");
      // noinspection UnstableApiUsage
      Files.write(Json.pretty().writeValueAsBytes(process), jsonFile);
      System.out.printf("Write %s%n", jsonFile.getAbsolutePath()); // NOSONAR

      File yamlFile = new File(folder, "swagger.yaml");
      // noinspection UnstableApiUsage
      Files.write(Yaml.pretty().writeValueAsBytes(process), yamlFile);
      System.out.printf("Write %s%n", yamlFile.getAbsolutePath()); // NOSONAR
   }
}
