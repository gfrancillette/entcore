/*
 * Copyright © WebServices pour l'Éducation, 2015
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.auth.services.impl;

import fr.wseduc.webutils.Either;
import org.opensaml.saml2.core.Assertion;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

public class SSOAten extends AbstractSSOProvider {

	@Override
	public void execute(Assertion assertion, Handler<Either<String, JsonObject>> handler) {
		if (!validConditions(assertion, handler)) return;

		String vector = getAttribute(assertion, "FrEduVecteur");
		if (vector == null) {
			handler.handle(new Either.Left<String, JsonObject>("invalid.vector"));
			return;
		}

		String values[] = vector.split("\\|");
		if (values.length > 4 && !values[3].trim().isEmpty() && !values[4].trim().isEmpty()) { // Eleve, PersRelEleve
			JsonObject params = new JsonObject()
					.putString("attachmentId", values[3])
					.putString("UAI", values[4]);
			String query;
			switch (values[0]) {
				case "1": // PersRelEleve 1d
				case "2": // PersRelEleve 2d
					query = "MATCH (:User {attachmentId: {attachmentId}})-[:RELATED]->(u:User)-[:IN]->(:ProfileGroup)" +
							"-[:DEPENDS]->(s:Structure) " +
							"WHERE HEAD(u.profiles) = 'Relative' AND s.UAI = {UAI} " +
							"AND u.firstName = {firstName} AND u.lastName = {lastName} ";
					params.putString("firstName",values[2]).putString("lastName", values[1]);
					break;
				case "3": // Eleve 1d
				case "4": // Eleve 2d
					query = "MATCH (u:User {attachmentId: {attachmentId}})-[:IN]->(:ProfileGroup)-[:DEPENDS]->(s:Structure) " +
							"WHERE HEAD(u.profiles) = 'Student' AND s.UAI = {UAI} ";
					break;
				default:
					handler.handle(new Either.Left<String, JsonObject>("invalid.user.profile"));
					return;
			}
			executeQuery(query, params, assertion, handler);
		} else {
			handler.handle(new Either.Left<String, JsonObject>("invalid.vector"));
		}
	}

}
