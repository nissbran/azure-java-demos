package aoai.demos.functions;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChatAppWithFunctions {

    public static final String BASE_URL = "https://swapi.dev/api/";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String STAR_WARS_API_FUNCTION_CALL_NAME = "call_starwars_api";

    public static void main(String[] args) {

        var azureOpenaiKey = "your api key";
        var endpoint = "https://  --- .openai.azure.com/";
        var deploymentOrModelId = "gpt-35-turbo";

        var client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(azureOpenaiKey))
                .buildClient();

        var systemMessage = new ChatMessage(ChatRole.SYSTEM,
                "You are a helpful assistant that helps find information about starships in Star Wars.");

        List<ChatMessage> chatHistoryMessages = new ArrayList<>();

        var scanner = new Scanner(System.in);
        System.out.println("Enter a question to the star wars assistant. Enter /q to quit.");
        System.out.print("You: ");

        while (true) {
            var consoleLine = scanner.nextLine();

            if (consoleLine.equals("/q")) {
                break;
            }

            List<ChatMessage> chatInputMessages = new ArrayList<>();

            chatInputMessages.add(systemMessage);
            chatInputMessages.addAll(chatHistoryMessages);

            var question = new ChatMessage(ChatRole.USER, consoleLine);
            chatInputMessages.add(question);

            var options = new ChatCompletionsOptions(chatInputMessages);
            options.setMaxTokens(2000);
            options.setTemperature(0.7d);
            options.setFunctionCall(FunctionCallConfig.AUTO);
            options.setFunctions(new ArrayList<FunctionDefinition>() {{
                add(new FunctionDefinition(STAR_WARS_API_FUNCTION_CALL_NAME)
                        .setDescription("Gets Star Wars starship information.")
                        .setParameters(getCallStarWarsApiFunctionDefinition()));
            }});

            ChatCompletions chatCompletions = client.getChatCompletions(deploymentOrModelId, options);

            var response = handleFunctionCallResponse(chatCompletions.getChoices(), chatInputMessages);

            if (response.function_call()) {
                chatInputMessages = response.messages();
                chatCompletions = client.getChatCompletions(deploymentOrModelId, new ChatCompletionsOptions(chatInputMessages));
                response = handleFunctionCallResponse(chatCompletions.getChoices(), chatInputMessages);
            }

            for (var message : response.messages()) {

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


    private static Map<String, Object> getCallStarWarsApiFunctionDefinition() {
        // Construct JSON in Map, or you can use create your own customized model.
        Map<String, Object> shipName = new HashMap<>();
        shipName.put("type", "string");
        shipName.put("description", "The name of the ship, e.g. CR90 corvette");
        Map<String, Object> properties = new HashMap<>();
        properties.put("ship_name", shipName);
        Map<String, Object> functionDefinition = new HashMap<>();
        functionDefinition.put("type", "object");
        functionDefinition.put("required", List.of("ship_name"));
        functionDefinition.put("properties", properties);
        return functionDefinition;
    }

    private static FunctionCallResponse handleFunctionCallResponse(List<ChatChoice> choices, List<ChatMessage> chatInputMessages) {

        boolean functionCallNeeded = false;
        List<ChatMessage> messages = new ArrayList<>();

        for (ChatChoice choice : choices) {
            ChatMessage choiceMessage = choice.getMessage();
            FunctionCall functionCall = choiceMessage.getFunctionCall();

            if (CompletionsFinishReason.FUNCTION_CALL.equals(choice.getFinishReason())) {
                System.out.printf("Function name: %s, arguments: %s.%n", functionCall.getName(), functionCall.getArguments());

                if (functionCall.getName().equals(STAR_WARS_API_FUNCTION_CALL_NAME)) {
                    SwapiFunctionCallingInput input = BinaryData.fromString(functionCall.getArguments()).toObject(SwapiFunctionCallingInput.class);

                    try {
                        HttpClient httpClient = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest
                                .newBuilder(URI.create(BASE_URL + "starships?search=" + URLEncoder.encode(input.ship_name(), StandardCharsets.UTF_8)))
                                .GET()
                                .setHeader("Content-Type", "application/json")
                                .build();

                        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                        SwapiResponse swapiResponse = toObject(response.body());
                        String shipResponse;
                        if (swapiResponse.count == 0) {
                            shipResponse = "No starship found.";
                            System.out.println("No starship found.");
                        }
                        else {
                            shipResponse = ToGptReadable(swapiResponse.results().get(0));
                        }

                        var functionCallMessage = new ChatMessage(ChatRole.FUNCTION, shipResponse)
                                .setName(STAR_WARS_API_FUNCTION_CALL_NAME);
                        chatInputMessages.add(functionCallMessage);
                        functionCallNeeded = true;

                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            else {
                messages.add(choiceMessage);
            }

        }
        if (functionCallNeeded) {
            return new FunctionCallResponse(functionCallNeeded, chatInputMessages);
        }
        else {
            return new FunctionCallResponse(functionCallNeeded, messages);
        }
    }

    private record FunctionCallResponse(boolean function_call, List<ChatMessage> messages) {
    }

    private static String ToGptReadable(SwapiResponse.StarShip starShip) {
        return String.format("Name: %s, Model: %s, Manufacturer: %s, Cost in credits: %s, Length: %s, Max atmosphering speed: %s, Crew: %s, Passengers: %s, Cargo capacity: %s, Consumables: %s, Hyperdrive rating: %s, MGLT: %s, Starship class: %s, Pilots: %s, Films: %s",
                starShip.name(),
                starShip.model(),
                starShip.manufacturer(),
                starShip.cost_in_credits(),
                starShip.length(),
                starShip.max_atmosphering_speed(),
                starShip.crew(),
                starShip.passengers(),
                starShip.cargo_capacity(),
                starShip.consumables(),
                starShip.hyperdrive_rating(),
                starShip.MGLT(),
                starShip.starship_class(),
                starShip.pilots(),
                starShip.films());
    }

    public static SwapiResponse toObject(InputStream inputStream) {
        try {
            return OBJECT_MAPPER.readValue(inputStream, SwapiResponse.class);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    public static record SwapiFunctionCallingInput(
            String ship_name) {
    }

    public record SwapiResponse(
            Integer count,
            String next,
            String previous,
            List<StarShip> results) {
        public record StarShip(
                String name,
                String model,
                String manufacturer,
                String cost_in_credits,
                String length,
                String max_atmosphering_speed,
                String crew,
                String passengers,
                String cargo_capacity,
                String consumables,
                String hyperdrive_rating,
                String MGLT,
                String starship_class,
                List<String> pilots,
                List<String> films,
                String created,
                String edited,
                String url
        ) {
        }
    }
}