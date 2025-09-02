package com.espacogeek.geek.services.impl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.espacogeek.geek.data.MediaDataController;
import com.espacogeek.geek.data.api.MediaApi;
import com.espacogeek.geek.models.ExternalReferenceModel;
import com.espacogeek.geek.models.MediaModel;
import com.espacogeek.geek.models.TypeReferenceModel;
import com.espacogeek.geek.repositories.MediaRepository;
import com.espacogeek.geek.repositories.MediaRepositoryCustom;
import com.espacogeek.geek.services.MediaCategoryService;
import com.espacogeek.geek.services.MediaService;
import com.espacogeek.geek.services.TypeReferenceService;
import com.espacogeek.geek.utils.Utils;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.transaction.Transactional;

/**
 * A Implementation class of MediaService @see MediaService
 */
@Service
public class MediaServiceImpl implements MediaService {
    @SuppressWarnings("rawtypes")
    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MediaCategoryService mediaCategoryService;

    @Autowired @Qualifier("serieController")
    private MediaDataController serieController;

    @Autowired @Qualifier("genericMediaDataController")
    private MediaDataController genericMediaDataController;

    @Autowired
    private MediaApi gamesAndVNsAPI;

    @Autowired
    private TypeReferenceService typeReferenceService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MediaServiceImpl.class);

    /**
     * @see MediaService#save(MediaModel)
     */
    @SuppressWarnings("unchecked")
    @Override
    public MediaModel save(MediaModel media) {
        return (MediaModel) this.mediaRepository.save(media);
    }

    /**
     * @see MediaService#saveAll(List<MediaModel>)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<MediaModel> saveAll(List<MediaModel> medias) {
        return this.mediaRepository.saveAll(medias);
    }

    /**
     * @see MediaService#findSerieByIdOrName(Integer, String, Pageable)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Page<MediaModel> findSerieByIdOrName(Integer id, String name, Pageable pageable) {
        if (id != null) {
            return (Page<MediaModel>) this.mediaRepository.findById(id).orElseGet(null);
        }

        return this.mediaRepository.findMediaByNameOrAlternativeTitleAndMediaCategory(name, name,
                mediaCategoryService.findById(MediaDataController.SERIE_ID).get().getId(), pageable);
    }

    /**
     * @see MediaService#findSerieByIdOrName(Integer, String, Map<String, List<String>>, Pageable)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Page<MediaModel> findSerieByIdOrName(Integer id, String name, Map<String, List<String>> requestedFields, Pageable pageable) {
        var medias = new ArrayList<MediaModel>();

        if (id != null) {
            medias.add((MediaModel) this.mediaRepository.findById(id).orElseGet(null));
            return new PageImpl<>(medias, pageable, medias.size());
        }

        var results = mediaRepository.findMediaByNameOrAlternativeTitleAndMediaCategory(name, name, mediaCategoryService.findById(MediaDataController.SERIE_ID).get().getId(), requestedFields, pageable);

        return results;
    }

    /**
     * @see MediaService#findGameByIdOrName(Integer, String, Pageable)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Page<MediaModel> findGameByIdOrName(Integer id, String name, Pageable pageable) {
        if (id != null) {
            return (Page<MediaModel>) this.mediaRepository.findById(id).orElseGet(null);
        }

        return this.mediaRepository.findMediaByNameOrAlternativeTitleAndMediaCategory(name, name,
                mediaCategoryService.findById(MediaDataController.GAME_ID).get().getId(), pageable);
    }

    /**
     * @see MediaService#findById(Integer)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Optional<MediaModel> findById(Integer idMedia) {
        return this.mediaRepository.findById(idMedia);
    }

    /**
     * @see MediaService#findByReferenceAndTypeReference(ExternalReferenceModel,
     *      TypeReferenceModel)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Optional<MediaModel> findByReferenceAndTypeReference(ExternalReferenceModel reference,
            TypeReferenceModel typeReference) {
        return this.mediaRepository.findOneMediaByExternalReferenceAndTypeReference(reference.getReference(),
                typeReference);
    }

    /**
     * @see MediaService#findByIdEager(Integer)
     */
    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public Optional<MediaModel> findByIdEager(Integer id) {
        var fieldList = new ArrayList<Field>();
        MediaModel media = (MediaModel) mediaRepository.findById(id).orElseGet(null);
        if (media == null) return Optional.empty();

        for (Field field : media.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
                fieldList.add(field);
            }
        }

        for (Field field : fieldList) {
            try {
                String getterName = "get" + capitalize(field.getName());
                Method getter = media.getClass().getMethod(getterName);
                var fieldValue = getter.invoke(media);
                if (fieldValue instanceof List) {
                    ((List<?>) fieldValue).size(); // This will initialize the collection
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return Optional.ofNullable(media);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);

    }

    /**
     * @see MediaService#randomArtwork()
     */
    @Override
    public Optional<String> randomArtwork() {
        long total = mediaRepository.count();
        if (total <= 0) return Optional.empty();

        int maxAttempts = (int) Math.min(total + 10, 200); // safety cap
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int randomIndex = ThreadLocalRandom.current().nextInt((int) total);
            var page = mediaRepository.findAll(PageRequest.of(randomIndex, 1));
            if (page.isEmpty()) continue;

            MediaModel media = (MediaModel) page.getContent().getFirst();
            if (media == null) continue;

            if (media.getBanner() != null && !media.getBanner().isEmpty()) {
                return Optional.of(media.getBanner());
            }

            try {
                // Try to fetch/update artworks for this media
                Integer categoryId = media.getMediaCategory() != null ? media.getMediaCategory().getId() : null;
                if (categoryId == null) continue;

                MediaModel updated = media;
                switch (categoryId) {
                    case MediaDataController.GAME_ID:
                    case MediaDataController.VN_ID:
                        updated = Utils
                            .updateGenericMedia(
                                Arrays.asList(media),
                                genericMediaDataController,
                                typeReferenceService.findById(MediaDataController.IGDB_ID).orElseThrow(),
                                gamesAndVNsAPI
                            )
                            .getFirst();
                        break;
                    case MediaDataController.SERIE_ID:
                        updated = Utils.updateMedia(Arrays.asList(media), serieController).getFirst();
                        break;
                    default:
                        // Unsupported category for artwork update in this flow, skip
                        break;
                }

                if (updated != null && updated.getBanner() != null && !updated.getBanner().isEmpty()) {
                    return Optional.of(updated.getBanner());
                }
            } catch (Exception ex) {
                log.warn("Failed to update artwork for media id={}: {}", media.getId(), ex.getMessage());
                // continue to next attempt
            }
        }

        return Optional.empty();
    }

    @Override
    public Page<MediaModel> findMovieByIdOrName(Integer id, String name, Map<String, List<String>> requestedFields, Pageable pageable) {
        var medias = new ArrayList<MediaModel>();

        if (id != null) {
            medias.add((MediaModel) this.mediaRepository.findById(id).orElseGet(null));
            return new PageImpl<>(medias, pageable, medias.size());
        }

        var results = mediaRepository.findMediaByNameOrAlternativeTitleAndMediaCategory(name, name, mediaCategoryService.findById(MediaDataController.MOVIE_ID).get().getId(), pageable);

        return results;
    }
}
