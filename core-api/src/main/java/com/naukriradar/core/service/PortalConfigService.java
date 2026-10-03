package com.naukriradar.core.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.dto.request.PortalConfigRequest;
import com.naukriradar.core.dto.response.PortalConfigResponse;
import com.naukriradar.core.mapper.PortalConfigMapper;
import com.naukriradar.core.model.PortalConfig;
import com.naukriradar.core.repository.PortalConfigRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for apply portals. Applications already created keep the risk they got. */
@Service
public class PortalConfigService {

	private static final Pattern HOST = Pattern.compile("(?=.{4,200}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");

	private final PortalConfigRepository repository;
	private final PortalConfigMapper mapper;

	public PortalConfigService(PortalConfigRepository repository, PortalConfigMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<PortalConfigResponse> list() {
		return repository.findAllByOrderByDomainAsc().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public PortalConfigResponse get(String id) {
		return mapper.toResponse(load(id));
	}

	@Transactional
	public PortalConfigResponse create(PortalConfigRequest request) {
		String domain = normaliseDomain(request.domain());
		if (repository.existsByDomain(domain)) {
			throw duplicate(domain);
		}
		PortalConfig portal = new PortalConfig(domain);
		mapper.apply(request, portal);
		try {
			repository.saveAndFlush(portal);
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicate(domain);
		}
		return mapper.toResponse(portal);
	}

	@Transactional
	public PortalConfigResponse update(String id, PortalConfigRequest request) {
		PortalConfig portal = load(id);
		String domain = normaliseDomain(request.domain());
		if (!portal.getDomain().equals(domain)) {
			if (repository.existsByDomain(domain)) {
				throw duplicate(domain);
			}
			portal.setDomain(domain);
		}
		mapper.apply(request, portal);
		repository.saveAndFlush(portal);
		return mapper.toResponse(portal);
	}

	@Transactional
	public void delete(String id) {
		repository.delete(load(id));
	}

	/** "https://www.Careers.Acme.com/jobs?x=1" becomes "careers.acme.com". */
	static String normaliseDomain(String input) {
		String value = input.strip().toLowerCase(Locale.ROOT);
		value = value.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
		int end = value.length();
		for (char stop : new char[] { '/', '?', '#', ':' }) {
			int at = value.indexOf(stop);
			if (at >= 0) {
				end = Math.min(end, at);
			}
		}
		value = value.substring(0, end);
		if (value.startsWith("www.")) {
			value = value.substring(4);
		}
		if (value.endsWith(".")) {
			value = value.substring(0, value.length() - 1);
		}
		if (!HOST.matcher(value).matches()) {
			throw new BadRequestException("'" + input.strip() + "' isn't a domain like careers.example.com.");
		}
		return value;
	}

	private PortalConfig load(String id) {
		return repository.findById(id).orElseThrow(() -> new NotFoundException("No portal " + id + "."));
	}

	private static ConflictException duplicate(String domain) {
		return new ConflictException("A portal for " + domain + " already exists.");
	}

}
