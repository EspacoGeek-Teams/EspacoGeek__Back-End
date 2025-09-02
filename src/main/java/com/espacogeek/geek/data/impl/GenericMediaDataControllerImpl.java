package com.espacogeek.geek.data.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.context.annotation.Lazy;

import com.espacogeek.geek.data.MediaDataController;
import com.espacogeek.geek.data.api.MediaApi;
import com.espacogeek.geek.exception.MediaAlreadyExist;
import com.espacogeek.geek.models.AlternativeTitleModel;
import com.espacogeek.geek.models.ExternalReferenceModel;
import com.espacogeek.geek.models.GenreModel;
import com.espacogeek.geek.models.MediaCategoryModel;
import com.espacogeek.geek.models.MediaModel;
import com.espacogeek.geek.models.SeasonModel;
import com.espacogeek.geek.models.TypeReferenceModel;
import com.espacogeek.geek.services.AlternativeTitlesService;
import com.espacogeek.geek.services.ExternalReferenceService;
import com.espacogeek.geek.services.GenreService;
import com.espacogeek.geek.services.MediaService;
import com.espacogeek.geek.services.SeasonService;

import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;

@Component("genericMediaDataController")
@Qualifier("genericMediaDataController")
public class GenericMediaDataControllerImpl implements MediaDataController {

    @Autowired @Lazy
    protected MediaService mediaService;
    @Autowired
    private GenreService genreService;
    @Autowired
    private AlternativeTitlesService alternativeTitlesService;
    @Autowired
    private ExternalReferenceService externalReferenceService;
    @Autowired
    private SeasonService seasonService;

    public GenericMediaDataControllerImpl getInstance() {
        return this;
    }

    /**
     * @see MediaDataController#updateAllInformation(MediaModel, MediaModel, TypeReferenceModel, MediaApi)
     */
    @Override
    public MediaModel updateAllInformation(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        if (result == null) {
            var id = media.getExternalReference().stream()
                        .filter(externalReference -> externalReference.getTypeReference().getId().equals(typeReference.getId()))
                        .map(externalReference -> externalReference.getReference())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Reference not found for the given typeReference"));
            result = mediaApi.getDetails(Integer.valueOf(id));
        }

        if (result == null) {
            return media;
        }

        updateBasicAttributes(media, result, typeReference, mediaApi);
        updateArtworks(media, result, typeReference, mediaApi);

        media.setUpdateAt(Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
        mediaService.save(media);

        updateAlternativeTitles(media, result, typeReference, mediaApi);
        updateExternalReferences(media, result, typeReference, mediaApi);
        updateGenres(media, result, typeReference, mediaApi);
        updateSeason(media, result, typeReference, mediaApi);

        return media;
    }

    @Override
    public MediaModel updateArtworks(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        MediaModel rawArtwork = new MediaModel();

        if (result == null) {
            var externalReferences = media.getExternalReference();
            for (ExternalReferenceModel externalReference : externalReferences) {
                if (externalReference.getTypeReference().getId().equals(typeReference.getId())) {
                    rawArtwork = mediaApi.getArtwork(Integer.valueOf(externalReference.getReference()));
                }
            }
        } else {
            rawArtwork = result;
        }

        if (rawArtwork == null) return media;

        media.setCover(rawArtwork.getCover());
        media.setBanner(rawArtwork.getBanner());

        return media;
    }

    @Override
    public List<AlternativeTitleModel> updateAlternativeTitles(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        List<AlternativeTitleModel> allAlternativeTitles = new ArrayList<>();

        if (result == null) {
            for (ExternalReferenceModel reference : media.getExternalReference()) {
                if (reference.getTypeReference().equals(typeReference)) {
                    try {
                        allAlternativeTitles = mediaApi.getAlternativeTitles(Integer.valueOf(reference.getReference()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            allAlternativeTitles = result.getAlternativeTitles();
        }

        if (CollectionUtils.isEmpty(allAlternativeTitles)) return media.getAlternativeTitles();

        if (media.getAlternativeTitles() == null) media.setAlternativeTitles(new ArrayList<>());
        for (AlternativeTitleModel title : allAlternativeTitles) {
            if (media.getAlternativeTitles().stream().noneMatch((alternativeTitle) -> alternativeTitle.getName().equals(title.getName()))) {
                media.getAlternativeTitles().add(new AlternativeTitleModel(null, title.getName(), media));
            }
        }

        alternativeTitlesService.saveAll(media.getAlternativeTitles());
        return media.getAlternativeTitles();
    }

    @Override
    public List<ExternalReferenceModel> updateExternalReferences(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        List<ExternalReferenceModel> rawExternalReferences = new ArrayList<ExternalReferenceModel>();

        if (result == null) {
            for (ExternalReferenceModel reference : media.getExternalReference()) {
                if (reference.getTypeReference().equals(typeReference)) {
                    try {
                        rawExternalReferences = mediaApi.getExternalReference(Integer.valueOf(reference.getReference()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            rawExternalReferences = result.getExternalReference();
        }

        if (CollectionUtils.isEmpty(rawExternalReferences)) return media.getExternalReference();

        if (media.getExternalReference() == null) media.setExternalReference(new ArrayList<>());
        for (ExternalReferenceModel reference : rawExternalReferences) {
            if (CollectionUtils.isEmpty(media.getExternalReference()) || media.getExternalReference().stream().noneMatch((eReference) -> eReference.getReference().equals(reference.getReference()))) {
                reference.setMedia(media);
                media.getExternalReference().add(reference);
            }
        }

        externalReferenceService.saveAll(media.getExternalReference());

        return media.getExternalReference();
    }

    @Override
    public List<GenreModel> updateGenres(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        List<GenreModel> rawGenres = new ArrayList<>();

        if (result == null) {
            for (ExternalReferenceModel reference : media.getExternalReference()) {
                if (reference.getTypeReference().equals(typeReference)) {
                    try {
                        rawGenres = mediaApi.getGenre(Integer.valueOf(reference.getReference()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            rawGenres = result.getGenre();
        }

        if (CollectionUtils.isEmpty(rawGenres)) return media.getGenre();

        rawGenres.forEach((rawGenre) -> {
            if (media.getGenre().stream().noneMatch((genre) -> genre.getId().equals(rawGenre.getId()))) {
                rawGenre.setMedias(new ArrayList<>(Arrays.asList(media)));
                media.getGenre().add(rawGenre);
            }
        });

        genreService.saveAll(media.getGenre());
        return media.getGenre();
    }

    @Override
    public List<SeasonModel> updateSeason(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        List<SeasonModel> seasons = new ArrayList<>();
        List<SeasonModel> rawSeasons = new ArrayList<>();

        if (result == null) {
            for (ExternalReferenceModel reference : media.getExternalReference()) {
                if (reference.getTypeReference().equals(typeReference)) {
                    rawSeasons = mediaApi.getSeason(Integer.valueOf(reference.getReference()));
                }
            }
        } else {
            rawSeasons = result.getSeason();
        }

        if (CollectionUtils.isEmpty(rawSeasons)) return media.getSeason();

        rawSeasons.forEach((rawSeason) -> {
            if (!media.getSeason().stream().anyMatch((season) -> season.getName().equals(rawSeason.getName()))) {
                seasons.add(new SeasonModel(null, rawSeason.getName(), rawSeason.getAirDate(), null, rawSeason.getAbout(), rawSeason.getCover(), rawSeason.getSeasonNumber(), rawSeason.getEpisodeCount(), media));
            }
        });

        var savedSeasons = seasonService.saveAll(seasons);
        List<SeasonModel> newSeasons = media.getSeason() == null ? new ArrayList<>() : media.getSeason();
        newSeasons.addAll(savedSeasons == null ? new ArrayList<>() : savedSeasons);

        return newSeasons;
    }

    @Override
    public List<MediaModel> searchMedia(String search, MediaApi mediaApi, TypeReferenceModel typeReference, MediaCategoryModel mediaCategory) {
        var rawMediaSearchList = mediaApi.doSearch(search, mediaCategory);
        var result = new ArrayList<MediaModel>();

        for (MediaModel mediaSearch : rawMediaSearchList) {
            var media = new MediaModel();
            media.setMediaCategory(mediaCategory);

            try {
                media = createMediaIfNotExistAndIfExistReturnIt(mediaSearch, typeReference);
                media = mediaSearch;

                if (media != null) {
                    updateExternalReferences(media, mediaSearch, typeReference, mediaApi);
                    updateAlternativeTitles(media, mediaSearch, typeReference, mediaApi);
                    updateArtworks(media, mediaSearch, typeReference, mediaApi);
                    updateBasicAttributes(media, mediaSearch, typeReference, mediaApi);

                    mediaService.save(media);
                }

                result.add(media);

            } catch (MediaAlreadyExist e) {
                media = mediaService.findByReferenceAndTypeReference(mediaSearch.getExternalReference().getFirst(), typeReference).orElseThrow();

                result.add(media);
            }
        }

        return result;
    }

    @Override
    public MediaModel updateBasicAttributes(MediaModel media, MediaModel result, TypeReferenceModel typeReference, MediaApi mediaApi) {
        MediaModel rawMedia = new MediaModel();

        if (result == null) {
            for (ExternalReferenceModel reference : media.getExternalReference()) {
                if (reference.getTypeReference().equals(typeReference)) {
                    rawMedia = mediaApi.getUpdateBasicAttributes(Integer.parseInt(reference.getReference()));
                    break;
                }
            }
        } else {
            rawMedia = result;
        }

        if (rawMedia == null) return media;

        for (Field field : media.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            for (Field rawField : rawMedia.getClass().getDeclaredFields()) {
                rawField.setAccessible(true);
                if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class) || field.isAnnotationPresent(Id.class)) continue;
                if (field.getName().equals(rawField.getName())) {
                    try {
                        field.set(media, rawField.get(rawMedia));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return media;
    }

    /**
     * Save the provided media if not exist in database, but <strong>Media (<code>MediaModel</code>) must have only one <code>ExternalReference</code>.</strong>
     *
     * @param media
     * @param typeReference
     * @return <code>MediaModel</code>.
     * @throws MediaAlreadyExist when media already exist in database.
     *
     * @see MediaDataController#createMediaIfNotExistAndIfExistReturnIt(MediaModel, TypeReferenceModel)
     */
    @Override
    public MediaModel createMediaIfNotExistAndIfExistReturnIt(MediaModel media, TypeReferenceModel typeReference) {

        for (ExternalReferenceModel ereference : media.getExternalReference()) {
            var external = externalReferenceService.findByReferenceAndType(ereference.getReference(), typeReference);
            if (external == null || external.isEmpty()) {
                media.setId(null);
                return mediaService.save(media);
            } else {
                if (external.orElseThrow().getMedia() != null) {
                    throw new MediaAlreadyExist(external.orElseThrow().getMedia().toString());
                }
                return external.orElseThrow().getMedia();
            }
        }

        throw new MediaAlreadyExist(media.toString());
    }
}
