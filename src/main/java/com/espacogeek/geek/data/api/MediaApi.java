package com.espacogeek.geek.data.api;

import java.util.List;

import org.json.simple.JSONArray;

import com.espacogeek.geek.models.AlternativeTitleModel;
import com.espacogeek.geek.models.ExternalReferenceModel;
import com.espacogeek.geek.models.GenreModel;
import com.espacogeek.geek.models.MediaCategoryModel;
import com.espacogeek.geek.models.MediaModel;
import com.espacogeek.geek.models.SeasonModel;

import info.movito.themoviedbapi.model.keywords.Keyword;

public interface MediaApi {
    public final static String URL_IMAGE_TMDB = "https://image.tmdb.org/t/p/original";
    public final static Integer TMDB_API_KEY_ID = 1;
    public final static Integer IGDB_CLIENT_ID = 2;
    public final static Integer IGDB_TOKEN = 3;
    public final static Integer IGDB_CLIENT_SECRET = 4;
    public final static Integer NINJA_QUOTE_API_KEY = 5;

    default public JSONArray updateTitles() {
        throw new UnsupportedOperationException();
    }

    default public MediaModel getDetails(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public MediaModel getArtwork(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public MediaModel getUpdateBasicAttributes(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<Keyword> getKeyword(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<AlternativeTitleModel> getAlternativeTitles(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<ExternalReferenceModel> getExternalReference(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<GenreModel> getGenre(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<SeasonModel> getSeason(Integer id) {
        throw new UnsupportedOperationException();
    }

    default public List<MediaModel> doSearch(String search) {
        throw new UnsupportedOperationException();
    }

    default public List<MediaModel> doSearch(String search, MediaCategoryModel mediaCategoryModel) {
        throw new UnsupportedOperationException();
    }
}
