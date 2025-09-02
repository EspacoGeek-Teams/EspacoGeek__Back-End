package com.espacogeek.geek.data.api.impl;

import java.io.IOException;
import java.util.Optional;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.espacogeek.geek.data.api.MediaApi;
import com.espacogeek.geek.data.api.QuoteApi;
import com.espacogeek.geek.exception.GenericException;
import com.espacogeek.geek.models.ApiKeyModel;
import com.espacogeek.geek.models.QuoteModel;
import com.espacogeek.geek.services.ApiKeyService;

import jakarta.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Component("quoteController")
@Qualifier("quoteController")
public class QuoteApiImpl implements QuoteApi {
    @Autowired
    private ApiKeyService apiKeyService;
    private ApiKeyModel apiKey;
    private final static String URL_QUOTE = "https://api.api-ninjas.com/v1/quotes";
    private static final Logger logger = LoggerFactory.getLogger(QuoteApiImpl.class);


    @PostConstruct
    public void init() {
        Optional<ApiKeyModel> optionalApiKey = apiKeyService.findById(MediaApi.NINJA_QUOTE_API_KEY);
        if (optionalApiKey.isPresent()) {
            apiKey = optionalApiKey.get();
            logger.info("API key for Ninja Quote loaded successfully.");
        } else {
            logger.error("API key for Ninja Quote is missing or blank.");
        }
    }

    @Override
    public QuoteModel getRandomQuote() {
        var client = new OkHttpClient().newBuilder().build();
        Request request = null;
        try {
            request = new Request.Builder()
                    .url(URL_QUOTE)
                    .method("GET", null)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Api-Key", apiKey.getKey())
                    .build();
        } catch (Exception e) {
            throw new GenericException("Quote not found");
        }

        Response response = null;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            throw new GenericException("Quote not found");
        }

        var parser = new JSONParser();
        var jsonArray = new JSONArray();
        try {
            jsonArray = (JSONArray) parser.parse(response.body().string());
        } catch (ParseException | IOException e) {
            throw new GenericException("Quote not found");
        }

        var jsonObject = (JSONObject) jsonArray.getFirst();

        return new QuoteModel(jsonObject.get("quote").toString(), jsonObject.get("author").toString());
    }

}
