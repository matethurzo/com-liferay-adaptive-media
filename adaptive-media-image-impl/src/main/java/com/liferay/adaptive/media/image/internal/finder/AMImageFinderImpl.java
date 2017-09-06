/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.adaptive.media.image.internal.finder;

import com.liferay.adaptive.media.AMAttribute;
import com.liferay.adaptive.media.AdaptiveMedia;
import com.liferay.adaptive.media.finder.AMFinder;
import com.liferay.adaptive.media.finder.AMQuery;
import com.liferay.adaptive.media.image.configuration.AdaptiveMediaImageConfigurationEntry;
import com.liferay.adaptive.media.image.configuration.AdaptiveMediaImageConfigurationHelper;
import com.liferay.adaptive.media.image.finder.AMImageFinder;
import com.liferay.adaptive.media.image.finder.AMImageQueryBuilder;
import com.liferay.adaptive.media.image.internal.configuration.AdaptiveMediaImageAttributeMapping;
import com.liferay.adaptive.media.image.internal.processor.AdaptiveMediaImage;
import com.liferay.adaptive.media.image.internal.util.ImageProcessor;
import com.liferay.adaptive.media.image.model.AdaptiveMediaImageEntry;
import com.liferay.adaptive.media.image.processor.AdaptiveMediaImageAttribute;
import com.liferay.adaptive.media.image.processor.AdaptiveMediaImageProcessor;
import com.liferay.adaptive.media.image.service.AdaptiveMediaImageEntryLocalService;
import com.liferay.adaptive.media.image.url.AdaptiveMediaImageURLFactory;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileVersion;

import java.net.URI;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Adolfo Pérez
 */
@Component(
	immediate = true,
	property = "model.class.name=com.liferay.portal.kernel.repository.model.FileVersion",
	service = {AMFinder.class, AMImageFinder.class}
)
public class AMImageFinderImpl implements AMImageFinder {

	@Override
	public Stream<AdaptiveMedia<AdaptiveMediaImageProcessor>>
			getAdaptiveMediaStream(
				Function
					<AMImageQueryBuilder,
						AMQuery<FileVersion, AdaptiveMediaImageProcessor>>
							amImageQueryBuilderFunction)
		throws PortalException {

		if (amImageQueryBuilderFunction == null) {
			throw new IllegalArgumentException(
				"amImageQueryBuilder must be non null");
		}

		AMImageQueryBuilderImpl amImageQueryBuilderImpl =
			new AMImageQueryBuilderImpl();

		AMQuery<FileVersion, AdaptiveMediaImageProcessor> amQuery =
			amImageQueryBuilderFunction.apply(amImageQueryBuilderImpl);

		if (amQuery != AMImageQueryBuilderImpl.AM_QUERY) {
			throw new IllegalArgumentException(
				"Only queries built by the provided query builder are valid.");
		}

		FileVersion fileVersion = amImageQueryBuilderImpl.getFileVersion();

		if (!_imageProcessor.isMimeTypeSupported(fileVersion.getMimeType())) {
			return Stream.empty();
		}

		BiFunction<FileVersion, AdaptiveMediaImageConfigurationEntry, URI>
			uriFactory = _getURIFactory(amImageQueryBuilderImpl);

		AMImageQueryBuilder.ConfigurationStatus configurationStatus =
			amImageQueryBuilderImpl.getConfigurationStatus();

		Collection<AdaptiveMediaImageConfigurationEntry> configurationEntries =
			_configurationHelper.getAdaptiveMediaImageConfigurationEntries(
				fileVersion.getCompanyId(), configurationStatus.getPredicate());

		Predicate<AdaptiveMediaImageConfigurationEntry> filter =
			amImageQueryBuilderImpl.getConfigurationEntryFilter();

		Stream<AdaptiveMediaImageConfigurationEntry>
			adaptiveMediaImageConfigurationEntryStream =
				configurationEntries.stream();

		return adaptiveMediaImageConfigurationEntryStream.filter(
			configurationEntry ->
				filter.test(configurationEntry) &&
				_hasAdaptiveMedia(fileVersion, configurationEntry)
		).map(
			configurationEntry ->
				_createMedia(fileVersion, uriFactory, configurationEntry)
		).sorted(
			amImageQueryBuilderImpl.getComparator()
		);
	}

	@Reference(unbind = "-")
	public void setAdaptiveMediaImageConfigurationHelper(
		AdaptiveMediaImageConfigurationHelper configurationHelper) {

		_configurationHelper = configurationHelper;
	}

	@Reference(unbind = "-")
	public void setAdaptiveMediaImageEntryLocalService(
		AdaptiveMediaImageEntryLocalService imageEntryLocalService) {

		_imageEntryLocalService = imageEntryLocalService;
	}

	@Reference(unbind = "-")
	public void setAdaptiveMediaImageURLFactory(
		AdaptiveMediaImageURLFactory adaptiveMediaImageURLFactory) {

		_adaptiveMediaImageURLFactory = adaptiveMediaImageURLFactory;
	}

	@Reference(unbind = "-")
	public void setImageProcessor(ImageProcessor imageProcessor) {
		_imageProcessor = imageProcessor;
	}

	private AdaptiveMedia<AdaptiveMediaImageProcessor> _createMedia(
		FileVersion fileVersion,
		BiFunction<FileVersion, AdaptiveMediaImageConfigurationEntry, URI>
			uriFactory,
		AdaptiveMediaImageConfigurationEntry configurationEntry) {

		Map<String, String> properties = configurationEntry.getProperties();

		AMAttribute<Object, String> configurationUuidAMAttribute =
			AMAttribute.getConfigurationUuidAMAttribute();

		properties.put(
			configurationUuidAMAttribute.getName(),
			configurationEntry.getUUID());

		AMAttribute<Object, String> fileNameAMAttribute =
			AMAttribute.getFileNameAMAttribute();

		properties.put(
			fileNameAMAttribute.getName(), fileVersion.getFileName());

		AdaptiveMediaImageEntry imageEntry =
			_imageEntryLocalService.fetchAdaptiveMediaImageEntry(
				configurationEntry.getUUID(), fileVersion.getFileVersionId());

		if (imageEntry != null) {
			AMAttribute<AdaptiveMediaImageProcessor, Integer>
				imageHeightAMAttribute =
					AdaptiveMediaImageAttribute.IMAGE_HEIGHT;

			properties.put(
				imageHeightAMAttribute.getName(),
				String.valueOf(imageEntry.getHeight()));

			AMAttribute<AdaptiveMediaImageProcessor, Integer>
				imageWidthAMAttribute = AdaptiveMediaImageAttribute.IMAGE_WIDTH;

			properties.put(
				imageWidthAMAttribute.getName(),
				String.valueOf(imageEntry.getWidth()));

			AMAttribute<Object, String> contentTypeAMAttribute =
				AMAttribute.getContentTypeAMAttribute();

			properties.put(
				contentTypeAMAttribute.getName(), imageEntry.getMimeType());

			AMAttribute<Object, Integer> contentLengthAMAttribute =
				AMAttribute.getContentLengthAMAttribute();

			properties.put(
				contentLengthAMAttribute.getName(),
				String.valueOf(imageEntry.getSize()));
		}

		AdaptiveMediaImageAttributeMapping attributeMapping =
			AdaptiveMediaImageAttributeMapping.fromProperties(properties);

		return new AdaptiveMediaImage(
			() ->
				_imageEntryLocalService.getAdaptiveMediaImageEntryContentStream(
					configurationEntry, fileVersion),
			attributeMapping,
			uriFactory.apply(fileVersion, configurationEntry));
	}

	private BiFunction<FileVersion, AdaptiveMediaImageConfigurationEntry, URI>
		_getURIFactory(AMImageQueryBuilderImpl amImageQueryBuilderImpl) {

		if (amImageQueryBuilderImpl.hasFileVersion()) {
			return _adaptiveMediaImageURLFactory::createFileVersionURL;
		}

		return _adaptiveMediaImageURLFactory::createFileEntryURL;
	}

	private boolean _hasAdaptiveMedia(
		FileVersion fileVersion,
		AdaptiveMediaImageConfigurationEntry configurationEntry) {

		AdaptiveMediaImageEntry imageEntry =
			_imageEntryLocalService.fetchAdaptiveMediaImageEntry(
				configurationEntry.getUUID(), fileVersion.getFileVersionId());

		if (imageEntry == null) {
			return false;
		}

		return true;
	}

	private AdaptiveMediaImageURLFactory _adaptiveMediaImageURLFactory;
	private AdaptiveMediaImageConfigurationHelper _configurationHelper;
	private AdaptiveMediaImageEntryLocalService _imageEntryLocalService;
	private ImageProcessor _imageProcessor;

}