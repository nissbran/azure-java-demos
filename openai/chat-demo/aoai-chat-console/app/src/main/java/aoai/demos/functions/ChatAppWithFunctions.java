package aoai.demos.functions;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.models.*;
import com.azure.search.documents.util.SearchPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class ChatAppWithFunctions {

    public static final String BASE_URL = "https://swapi.dev/api/";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String STAR_WARS_API_FUNCTION_CALL_NAME = "call_starwars_api";
    public static final String STAR_WARS_VEHICLE_SEARCH_FUNCTION_CALL_NAME = "call_vehicle_search";

    public static SearchIndexClient searchIndexClient;
    public static OpenAIClient openAIClient;

    public static void main(String[] args) {

        var dotenv = Dotenv.load();
        var endpoint = dotenv.get("OPENAI_ENDPOINT");
        var azureOpenaiKey = dotenv.get("OPENAI_KEY");
        var deploymentOrModelId = "gpt-35-turbo";

        openAIClient = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(azureOpenaiKey))
                .buildClient();

        var azureSearchEndpoint = dotenv.get("AZURE_SEARCH_ENDPOINT");
        var azureSearchKey = dotenv.get("AZURE_SEARCH_KEY");

        searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(azureSearchEndpoint)
                .credential(new AzureKeyCredential(azureSearchKey))
                .buildClient();

        var systemMessage = new ChatMessage(ChatRole.SYSTEM,
                "You are a helpful assistant that helps find information about starships and vehicles in Star Wars.");

        List<ChatMessage> chatHistoryMessages = new ArrayList<>();

        var scanner = new Scanner(System.in);
        System.out.println("Enter a question to the star wars assistant. Enter /q to quit.");
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
            options.setFunctionCall(FunctionCallConfig.AUTO);
            options.setFunctions(new ArrayList<FunctionDefinition>() {{
                add(new FunctionDefinition(STAR_WARS_API_FUNCTION_CALL_NAME)
                        .setDescription("Gets Star Wars starship information.")
                        .setParameters(getCallStarWarsApiFunctionDefinition()));
                add(new FunctionDefinition(STAR_WARS_VEHICLE_SEARCH_FUNCTION_CALL_NAME)
                        .setDescription("Searches for a vehicle in Star Wars.")
                        .setParameters(getSearchVehicleFunctionDefinition()));
            }});

            ChatCompletions chatCompletions = openAIClient.getChatCompletions(deploymentOrModelId, options);
            var functionCallingHistory = new ArrayList<ChatMessage>();
            var response = handleFunctionCallResponse(chatCompletions.getChoices(), chatInputMessages, functionCallingHistory);

            if (response.function_call()) {
                chatInputMessages = response.messages();
                chatCompletions = openAIClient.getChatCompletions(deploymentOrModelId, new ChatCompletionsOptions(chatInputMessages));
                response = handleFunctionCallResponse(chatCompletions.getChoices(), chatInputMessages, functionCallingHistory);
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
                chatHistoryMessages.addAll(functionCallingHistory);
                chatHistoryMessages.add(question);
                chatHistoryMessages.add(new ChatMessage(ChatRole.ASSISTANT, assistantResponse));

                System.out.println();
                System.out.print("You: ");
            }
        }
    }


    private static FunctionCallResponse handleFunctionCallResponse(List<ChatChoice> choices, List<ChatMessage> chatInputMessages, List<ChatMessage> functionCallingHistory) {

        boolean functionCallWasMade = false;
        List<ChatMessage> messages = new ArrayList<>();

        for (ChatChoice choice : choices) {
            ChatMessage choiceMessage = choice.getMessage();

            if (CompletionsFinishReason.FUNCTION_CALL.equals(choice.getFinishReason())) {
                FunctionCall functionCall = choiceMessage.getFunctionCall();
                System.out.printf("Function name: %s, arguments: %s.%n", functionCall.getName(), functionCall.getArguments());

                if (functionCall.getName().equals(STAR_WARS_API_FUNCTION_CALL_NAME)) {
                    var functionCallResponseMessage = CallSwapiApi(functionCall);
                    chatInputMessages.add(functionCallResponseMessage);
                    functionCallingHistory.add(functionCallResponseMessage);
                    functionCallWasMade = true;
                }

                if (functionCall.getName().equals(STAR_WARS_VEHICLE_SEARCH_FUNCTION_CALL_NAME)) {
                    var functionCallResponseMessage = SearchVehicle(functionCall);
                    chatInputMessages.add(functionCallResponseMessage);
                    functionCallingHistory.add(functionCallResponseMessage);
                    functionCallWasMade = true;
                }
            } else {
                messages.add(choiceMessage);
            }
        }

        if (functionCallWasMade) {
            return new FunctionCallResponse(functionCallWasMade, chatInputMessages);
        } else {
            return new FunctionCallResponse(functionCallWasMade, messages);
        }
    }

    private static ChatMessage SearchVehicle(FunctionCall functionCall) {
        VehicleSearchFunctionCallingInput input = BinaryData.fromString(functionCall.getArguments()).toObject(VehicleSearchFunctionCallingInput.class);

        var searchQuery = input.search_query();
        var searchClient = searchIndexClient.getSearchClient("swapi-vehicle-index");

        var embeddingsResult = openAIClient.getEmbeddings("text-embedding-ada-002", new EmbeddingsOptions(new ArrayList<>() {{
            add(searchQuery);
        }}));

        List<Float> vectorizedResult = embeddingsResult.getData().get(0).getEmbedding().stream().map(Double::floatValue).toList();
        VectorQuery vectorQuery = new VectorizedQuery(vectorizedResult)
                .setKNearestNeighborsCount(3)
                .setFields("summary_vector");

        // Hybrid search with vector and semantic search
        SearchPagedIterable searchResults = searchClient.search(
                searchQuery,
                new SearchOptions()
                        .setQueryType(QueryType.SEMANTIC)
                        .setTop(3)
                        .setVectorSearchOptions(new VectorSearchOptions()
                                .setQueries(vectorQuery))
                        .setSemanticSearchOptions(new SemanticSearchOptions()
                                .setSemanticConfigurationName("default")
                                .setQueryAnswer(new QueryAnswer(QueryAnswerType.EXTRACTIVE))
                                .setQueryCaption(new QueryCaption(QueryCaptionType.EXTRACTIVE))
                                .setErrorMode(SemanticErrorMode.PARTIAL)
                                .setMaxWaitDuration(Duration.ofSeconds(5))),
                Context.NONE);

        var stringBuilder = new StringBuilder();
        int count = 0;
        for (var searchResult : searchResults) {
            count++;

            VehicleSearchResult result = searchResult.getDocument(VehicleSearchResult.class);
            System.out.println("Search result " + count + " : " + result.summary());
            stringBuilder.append(result.summary()).append("\n");
        }
        System.out.println("Total number of search results: " + count);

        return new ChatMessage(ChatRole.FUNCTION, stringBuilder.toString())
                .setName(STAR_WARS_VEHICLE_SEARCH_FUNCTION_CALL_NAME);
    }

    private static ChatMessage CallSwapiApi(FunctionCall functionCall) {
        SwapiApiFunctionCallingInput input = BinaryData.fromString(functionCall.getArguments()).toObject(SwapiApiFunctionCallingInput.class);

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
            } else {
                shipResponse = ToGptReadable(swapiResponse.results().get(0));
            }

            return new ChatMessage(ChatRole.FUNCTION, shipResponse)
                    .setName(STAR_WARS_API_FUNCTION_CALL_NAME);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
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


    private static Map<String, Object> getSearchVehicleFunctionDefinition() {
        // Construct JSON in Map, or you can use create your own customized model.
        Map<String, Object> searchQuery = new HashMap<>();
        searchQuery.put("type", "string");
        searchQuery.put("description", "The search query");
        Map<String, Object> properties = new HashMap<>();
        properties.put("search_query", searchQuery);
        Map<String, Object> functionDefinition = new HashMap<>();
        functionDefinition.put("type", "object");
        functionDefinition.put("required", List.of("search_query"));
        functionDefinition.put("properties", properties);
        return functionDefinition;
    }


    private record FunctionCallResponse(boolean function_call, List<ChatMessage> messages) {
    }

    private record VehicleSearchResult(
            String title,
            String summary,
            String model,
            String manufacturer
    ) {
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

    public record VehicleSearchFunctionCallingInput(
            String search_query) {
    }

    public record SwapiApiFunctionCallingInput(
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