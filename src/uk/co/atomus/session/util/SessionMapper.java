/*
 * (C) Copyright Atomus Ltd 2011 - All rights reserved.
 *
 * This software is provided "as is" without warranty of any kind,
 * express or implied, including but not limited to warranties as to
 * quality and fitness for a particular purpose. Atomus Ltd
 * does not support the Software, nor does it warrant that the Software
 * will meet your requirements or that the operation of the Software will
 * be uninterrupted or error free or that any defects will be
 * corrected. Nothing in this statement is intended to limit or exclude
 * any liability for personal injury or death caused by the negligence of
 * Atomus Ltd, its employees, contractors or agents.
 */

package uk.co.atomus.session.util;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.apache.catalina.Session;

import uk.co.atomus.session.TomcatSessionStorageEntity;

/**
 * Defines methods for mapping between Session and TomcatSessionStorageEntity
 *
 * @author Simon Dingle and Chris Derham
 *
 */
public interface SessionMapper {

	TomcatSessionStorageEntity mapToStorageEntityHeadersOnly(Session session, TomcatSessionStorageEntity storageEntity);

	TomcatSessionStorageEntity mapToStorageEntityWithData(Session session, TomcatSessionStorageEntity storageEntity)
			throws IOException;

	ObjectInputStream toObjectInputStream(TomcatSessionStorageEntity storageEntity, ClassLoader classLoader)
			throws IOException;

}
