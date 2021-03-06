package uk.ac.ebi.spot.controller.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceProcessor;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.common.exceptions.UnauthorizedUserException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.exception.InvalidCurieException;
import uk.ac.ebi.spot.exception.MappingException;
import uk.ac.ebi.spot.exception.UnknownDatasourceException;
import uk.ac.ebi.spot.model.Mapping;
import uk.ac.ebi.spot.model.MappingRequest;
import uk.ac.ebi.spot.security.model.OrcidUser;
import uk.ac.ebi.spot.security.model.Role;
import uk.ac.ebi.spot.security.repository.OrcidUserRepository;
import uk.ac.ebi.spot.service.MappingService;
import uk.ac.ebi.spot.util.MappingDistance;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Jupp
 * @since 14/06/2016
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Controller
@CrossOrigin
@RequestMapping("/api/mappings")
@ExposesResourceFor(Mapping.class)
public class MappingController implements
        ResourceProcessor<RepositoryLinksResource> {

    @Autowired
    private MappingService mappingService;

    @Autowired
    MappingAssembler mappingAssembler;

    @Autowired
    OrcidUserRepository userRepository;

    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<Mapping>> mappings(
            Pageable pageable,
            PagedResourcesAssembler resourceAssembler,
            @RequestParam(value = "fromId", required = false) String fromId,
            @RequestParam(value = "toId", required = false) String toId
    ) throws ResourceNotFoundException {

        Page<Mapping> page = null;
        if (fromId != null && toId != null) {
            page = mappingService.findMappingsById(fromId, toId, pageable);

        } else if (fromId != null) {
            page = mappingService.findMappingsById(fromId, pageable);
        }
        else {
            page = mappingService.getMappings(pageable);
        }

        return new ResponseEntity<>(resourceAssembler.toResource(page, mappingAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<Resource<Mapping>> getMapping(
            @PathVariable("id") String id) throws ResourceNotFoundException {

        Mapping page = mappingService.getMapping(id);

        return new ResponseEntity<>(mappingAssembler.toResource(page), HttpStatus.OK);
    }

//    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
//    HttpEntity<Mapping> saveMapping(@RequestParam(value = "apikey",required=false) String apikey, @RequestBody MappingRequest mappingRequest) throws ResourceNotFoundException, MappingException, InvalidCurieException {
//        if (userRepository.findByApikey(apikey) == null) {
//            throw new UnauthorizedUserException("User with this api key are not authorised to create mapings");
//        }
//        return new ResponseEntity<Mapping>(mappingService.save(mappingRequest), HttpStatus.OK);
//    }

    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
    HttpEntity<Iterable<Mapping>> saveAllMappings(@RequestParam(value = "apikey",required=false) String apikey, @RequestBody Collection<MappingRequest> mappingRequest) throws MappingException, InvalidCurieException {
        if (userRepository.findByApikey(apikey) == null) {
            throw new UnauthorizedUserException("User with this api key are not authorised to create mapings");
        }
        return new ResponseEntity<Iterable<Mapping>>(mappingService.saveAll(mappingRequest), HttpStatus.OK);
    }

//    @RequestMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.PUT)
//    HttpEntity<Datasource> updateMapping(@PathVariable("id") String id, @RequestBody Mapping mapping) throws ResourceNotFoundException, InvalidCurieException {
//        mapping.setMappingId(Long.getLong(id));
//        return new ResponseEntity<Datasource>(mappingService.update(datasource), HttpStatus.OK);
//    }

    @RequestMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    void deleteTerm(@RequestParam(value = "apikey",required=false) String apikey, @PathVariable("id") String id, @RequestBody Mapping mapping) throws ResourceNotFoundException {
        OrcidUser user = userRepository.findByApikey(apikey);
        if (user == null) {
            throw new UnauthorizedUserException("User with this api key are not authorised to create mapings");
        }
        if (user.getRole().equals(Role.ADMIN) || user.getOrcid().equals(mapping.getDatasource().getOrcid())) {
            // todo drop mappings
        }
        throw new UnsupportedOperationException("Can't delete mappings");
    }


    private static Object summaryCache = null;
    private static Map<String, Object> datasourceSummaryCache = new HashMap();
    @RequestMapping(path = "/summary", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    HttpEntity<String> getMappingSummary(
            @RequestParam(value = "datasource", required = false) String datasource
    ) throws ResourceNotFoundException {
        Object object = null;
        if (datasource != null) {
            if (!datasourceSummaryCache.containsKey(datasource))  {
                object = mappingService.getSummaryJson(datasource);
                datasourceSummaryCache.put(datasource, object);
            }
            object = datasourceSummaryCache.get(datasource);
        }
        else {
            if (summaryCache == null) {
                summaryCache =  mappingService.getSummaryJson();
            }
            object = summaryCache;
        }
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't get summary view");
        }
    }

    @CrossOrigin
    @RequestMapping(path = "/summary/counts", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    HttpEntity<String> getMappingSummaryCounts(
            @RequestParam(value = "datasource", required = true) String datasource,
            @RequestParam(value = "distance", required = false) Integer distance
    ) throws ResourceNotFoundException {

        if (distance == null) {
            distance = MappingDistance.DEFAULT_MAPPING_DISTANCE;
        }

        Map<String, Integer> object = mappingService.getMappedTargetCounts(datasource, distance);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't get summary counts view");
        }
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(MappingController.class).withRel("mappings"));
        return resource;
    }

//    @RequestMapping(path = "/search", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
//    HttpEntity<List<CypherMappingQueryBuilder.MappingResponse>> getOntology(@RequestBody MappingQuery mappingQuery) throws ResourceNotFoundException {
//        List<CypherMappingQueryBuilder.MappingResponse> mappingList =  mappingService.getMappingsSearch(mappingQuery.getId(), mappingQuery.getDistance(), mappingQuery.getSourcePrefix());
//
//        return new ResponseEntity<List<CypherMappingQueryBuilder.MappingResponse>>(mappingList, HttpStatus.OK);
//
//    }

    @ExceptionHandler ({DuplicateKeyException.class, UnknownDatasourceException.class, MappingException.class, InvalidCurieException.class})
    public void handleError(HttpServletResponse response, Exception exception) throws IOException {
        response.sendError(HttpStatus.UNPROCESSABLE_ENTITY.value(), exception.getMessage());
    }

}
