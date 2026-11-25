package com.chronon.recon.ingest.mapping;

import com.chronon.recon.exception.ColumnMappingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and indexes declarative mapping profiles from {@code classpath*:profiles/*.yml}.
 *
 * <p>Decouples bank statement file layouts from Java code so adding a new bank format
 * only requires dropping a YAML profile into the classpath without recompiling.</p>
 */
@Component
public class ProfileRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);
    private static final String LOCATION = "classpath*:profiles/*.yml";

    private final Map<String, MappingProfile> profiles = new LinkedHashMap<>();

    public ProfileRegistry(ResourcePatternResolver resourceResolver) throws IOException {
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Resource[] resources = resourceResolver.getResources(LOCATION);
        List<Resource> ordered = new ArrayList<>(List.of(resources));
        ordered.sort(Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

        for (Resource resource : ordered) {
            try (InputStream input = resource.getInputStream()) {
                MappingProfile profile = mapper.readValue(input, MappingProfile.class);
                if (profiles.putIfAbsent(profile.id(), profile) != null) {
                    throw new IllegalStateException("Duplicate mapping profile id '" + profile.id() + "'");
                }
                log.info("Loaded mapping profile '{}' for {}", profile.id(), profile.target().label());
            } catch (IOException | RuntimeException ex) {
                throw new IllegalStateException(
                        "Could not load mapping profile " + resource.getFilename() + ": " + ex.getMessage(), ex);
            }
        }

        if (profiles.isEmpty()) {
            throw new IllegalStateException("No mapping profiles found on the classpath at " + LOCATION);
        }
    }

    public List<MappingProfile> all() {
        return List.copyOf(profiles.values());
    }

    public List<MappingProfile> forTarget(MappingTarget target) {
        return profiles.values().stream()
                .filter(profile -> profile.target() == target)
                .sorted(Comparator.comparing(MappingProfile::id))
                .toList();
    }

    public MappingProfile get(String id) {
        MappingProfile profile = profiles.get(id);
        if (profile == null) {
            throw new ColumnMappingException("Unknown mapping profile '" + id + "'. Available profiles: "
                    + profiles.keySet());
        }
        return profile;
    }

    public MappingProfile defaultFor(MappingTarget target) {
        List<MappingProfile> candidates = forTarget(target);
        if (candidates.isEmpty()) {
            throw new ColumnMappingException("No mapping profile available for " + target.label());
        }
        return candidates.stream()
                .filter(MappingProfile::isDefault)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
