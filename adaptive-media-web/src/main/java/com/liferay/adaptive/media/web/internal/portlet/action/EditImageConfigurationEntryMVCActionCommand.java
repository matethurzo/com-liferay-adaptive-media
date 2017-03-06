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

package com.liferay.adaptive.media.web.internal.portlet.action;

import com.liferay.adaptive.media.ImageAdaptiveMediaConfigurationException;
import com.liferay.adaptive.media.image.configuration.ImageAdaptiveMediaConfigurationEntry;
import com.liferay.adaptive.media.image.configuration.ImageAdaptiveMediaConfigurationHelper;
import com.liferay.adaptive.media.image.service.AdaptiveMediaImageLocalService;
import com.liferay.adaptive.media.web.constants.AdaptiveMediaPortletKeys;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.FriendlyURLNormalizerUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Sergio González
 */
@Component(
	immediate = true,
	property = {
		"javax.portlet.name=" + AdaptiveMediaPortletKeys.ADAPTIVE_MEDIA,
		"mvc.command.name=/adaptive_media/edit_image_configuration_entry"
	},
	service = MVCActionCommand.class
)
public class EditImageConfigurationEntryMVCActionCommand
	extends BaseMVCActionCommand {

	@Override
	protected void doPermissionCheckedProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String name = ParamUtil.getString(actionRequest, "name");
		String uuid = ParamUtil.getString(actionRequest, "uuid");
		String maxHeight = ParamUtil.getString(actionRequest, "maxHeight");
		String maxWidth = ParamUtil.getString(actionRequest, "maxWidth");

		boolean automaticUuid = ParamUtil.getBoolean(
			actionRequest, "automaticUuid");

		if (automaticUuid) {
			uuid = _getAutomaticUuid(themeDisplay.getCompanyId(), name);
		}

		Map<String, String> properties = new HashMap<>();

		properties.put("max-height", maxHeight);
		properties.put("max-width", maxWidth);

		Optional<ImageAdaptiveMediaConfigurationEntry>
			configurationEntryOptional =
				_imageAdaptiveMediaConfigurationHelper.
					getImageAdaptiveMediaConfigurationEntry(
						themeDisplay.getCompanyId(), uuid);

		try {
			if (configurationEntryOptional.isPresent()) {
				ImageAdaptiveMediaConfigurationEntry configurationEntry =
					configurationEntryOptional.get();

				if (!_isConfigurationEntryEditable(
						themeDisplay.getCompanyId(),
						configurationEntryOptional.get())) {

					uuid = configurationEntry.getUUID();

					properties = configurationEntry.getProperties();
				}

				_imageAdaptiveMediaConfigurationHelper.
					updateImageAdaptiveMediaConfigurationEntry(
						themeDisplay.getCompanyId(), name, uuid, properties);
			}
			else {
				_imageAdaptiveMediaConfigurationHelper.
					addImageAdaptiveMediaConfigurationEntry(
						themeDisplay.getCompanyId(), name, uuid, properties);
			}
		}
		catch (ImageAdaptiveMediaConfigurationException iamce) {
			SessionErrors.add(actionRequest, iamce.getClass());
		}
	}

	@Reference(unbind = "-")
	protected void setImageAdaptiveMediaConfigurationHelper(
		ImageAdaptiveMediaConfigurationHelper
			imageAdaptiveMediaConfigurationHelper) {

		_imageAdaptiveMediaConfigurationHelper =
			imageAdaptiveMediaConfigurationHelper;
	}

	private String _getAutomaticUuid(long companyId, String name) {
		String normalizedName = FriendlyURLNormalizerUtil.normalize(name);

		String curUuid = normalizedName;

		for (int i = 1;; i++) {
			Optional<ImageAdaptiveMediaConfigurationEntry>
				imageAdaptiveMediaConfigurationEntryOptional =
					_imageAdaptiveMediaConfigurationHelper.
						getImageAdaptiveMediaConfigurationEntry(
							companyId, curUuid);

			if (!imageAdaptiveMediaConfigurationEntryOptional.isPresent()) {
				break;
			}

			String suffix = StringPool.DASH + i;

			curUuid = normalizedName + suffix;
		}

		return curUuid;
	}

	private boolean _isConfigurationEntryEditable(
		long companyId,
		ImageAdaptiveMediaConfigurationEntry configurationEntry) {

		int adaptiveMediaImagesCount =
			_imageLocalService.getAdaptiveMediaImagesCount(
				companyId, configurationEntry.getUUID());

		if (adaptiveMediaImagesCount == 0) {
			return true;
		}

		return false;
	}

	private ImageAdaptiveMediaConfigurationHelper
		_imageAdaptiveMediaConfigurationHelper;

	@Reference
	private AdaptiveMediaImageLocalService _imageLocalService;

}