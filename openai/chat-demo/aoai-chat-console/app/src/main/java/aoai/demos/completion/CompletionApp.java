package aoai.demos.completion;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CompletionApp {

    public static void main(String[] args) {

        var dotenv = Dotenv.load();
        var azureOpenaiKey = dotenv.get("OPENAI_KEY");
        var endpoint = dotenv.get("OPENAI_ENDPOINT");
        var deploymentOrModelId = "instruct";

        var client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(azureOpenaiKey))
                .buildClient();




    }
}
