package aoai.demos.chat;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ChatApp {

    public static void main(String[] args) {

        var dotenv = Dotenv.load();
        var azureOpenaiKey = dotenv.get("OPENAI_KEY");
        var endpoint = dotenv.get("OPENAI_ENDPOINT");
        var deploymentOrModelId = "gpt-35-turbo";

        var client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(azureOpenaiKey))
                .buildClient();

        var systemMessage = new ChatMessage(ChatRole.SYSTEM, "You are a helpful assistant. You will talk like a pirate.");

        List<ChatMessage> chatHistoryMessages = new ArrayList<>();

        var scanner = new Scanner(System.in);
        System.out.println("Enter a question to the pirate assistant. Enter /q to quit.");
        System.out.print("You: ");

        while (true) {
            var consoleLine = scanner.nextLine();

            if (consoleLine.equals("/q")) {
                break;
            }
            if (consoleLine.equals("/clear")) {
                chatHistoryMessages.clear();
                System.out.println("Cleared history");
                System.out.print("You: ");
                continue;
            }

            List<ChatMessage> chatInputMessages = new ArrayList<>();

            chatInputMessages.add(systemMessage);
            chatInputMessages.addAll(chatHistoryMessages);

            var question = new ChatMessage(ChatRole.USER, consoleLine);
            chatInputMessages.add(question);

            var options = new ChatCompletionsOptions(chatInputMessages);
            options.setMaxTokens(2000);
            options.setTemperature(0.7d);
            ChatCompletions chatCompletions = client.getChatCompletions(deploymentOrModelId, options);

            for (ChatChoice choice : chatCompletions.getChoices()) {

                ChatMessage message = choice.getMessage();
                var assistantResponse = message.getContent();
                System.out.println("Assistant:");
                System.out.println(assistantResponse);

                CompletionsUsage usage = chatCompletions.getUsage();
                System.out.printf("Usage: number of prompt token is %d, "
                                + "number of completion token is %d, and number of total tokens in request and response is %d.%n",
                        usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

                // Add history to keep context
                chatHistoryMessages.add(question);
                chatHistoryMessages.add(new ChatMessage(ChatRole.ASSISTANT, assistantResponse));

                System.out.println();
                System.out.print("You: ");
            }
        }
    }
}