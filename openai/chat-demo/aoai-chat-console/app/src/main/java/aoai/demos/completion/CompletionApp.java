package aoai.demos.completion;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CompletionApp {

    public static void main(String[] args) {

        var azureOpenaiKey = "your api key";
        var endpoint = "https://  --- .openai.azure.com/";
        var deploymentOrModelId = "instruct";

        var client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(azureOpenaiKey))
                .buildClient();




    }
}
