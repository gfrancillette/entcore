package org.entcore.workspace.service.impl;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.FileUtils;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;
import org.entcore.common.user.RepositoryEvents;
import org.entcore.workspace.dao.DocumentDao;
import org.entcore.workspace.dao.RackDao;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceRepositoryEvents implements RepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(WorkspaceRepositoryEvents.class);
	private final MongoDb mongo = MongoDb.getInstance();
	private final EventBus eb;
	private final String gridfsAddress;
	private final Vertx vertx;

	public WorkspaceRepositoryEvents(Vertx vertx, String gridfsAddress) {
		this.eb = Server.getEventBus(vertx);
		this.gridfsAddress = gridfsAddress;
		this.vertx = vertx;
	}

	@Override
	public void exportResources(final String exportId, final String userId, JsonArray g,
			final String exportPath, final String locale) {
		log.debug("Workspace export resources.");
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(userId).get());
		for (Object o : g) {
			if (!(o instanceof String)) continue;
			String gpId = (String) o;
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		QueryBuilder b = new QueryBuilder().or(
				QueryBuilder.start("owner").is(userId).get(),
				QueryBuilder.start("shared").elemMatch(
						new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
				).get());
		final JsonObject keys = new JsonObject().putNumber("file", 1).putNumber("name", 1);
		mongo.find(DocumentDao.DOCUMENTS_COLLECTION, MongoQueryBuilder.build(b), null,
				keys, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				final JsonObject exported = new JsonObject()
						.putString("action", "exported")
						.putString("exportId", exportId)
						.putString("locale", locale)
						.putString("status", "error");
				final JsonArray documents = event.body().getArray("results");
				if ("ok".equals(event.body().getString("status")) && documents != null) {
					QueryBuilder b = QueryBuilder.start("to").is(userId);
					mongo.find(RackDao.RACKS_COLLECTION, MongoQueryBuilder.build(b), null, keys,
							new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							JsonArray racks = event.body().getArray("results");
							if ("ok".equals(event.body().getString("status")) && racks != null) {
								final JsonObject alias = new JsonObject();
								final String [] ids = new String[racks.size() + documents.size()];
								for (int i = 0; i < documents.size(); i++) {
									JsonObject j = documents.get(i);
									ids[i] = j.getString("file");
									alias.putString(ids[i], j.getString("name"));
								}
								for (int i = 0; i < racks.size(); i++) {
									JsonObject j = racks.get(i);
									ids[i] = j.getString("file");
									alias.putString(ids[i], j.getString("name"));
								}
								exportFiles(alias, ids, exportPath, locale, exported);
							} else {
								log.error(event.body().getString("message"));
								eb.publish("entcore.export", exported);
							}
						}
					});
				} else {
					log.error(event.body().getString("message"));
					eb.publish("entcore.export", exported);
				}
			}
		});
	}

	private void exportFiles(final JsonObject alias, final String[] ids,
			String exportPath, String locale, final JsonObject exported) {
		createExportDirectory(exportPath, locale, new Handler<String>() {
			@Override
			public void handle(String path) {
				if (path != null) {
					QueryBuilder q = QueryBuilder.start("_id").in(ids);
					JsonObject e = new JsonObject()
							.putString("action", "write")
							.putString("path", path)
							.putObject("alias", alias)
							.putObject("query", MongoQueryBuilder.build(q));
					if (log.isDebugEnabled()) {
						log.debug(e.encode());
					}
					eb.send(gridfsAddress + ".json", e, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if ("ok".equals(event.body().getString("status"))) {
								exported.putString("status", "ok");
								eb.publish("entcore.export", exported);
							} else {
								log.error(event.body().getString("message"));
								eb.publish("entcore.export", exported);
							}
						}
					});
				} else {
					log.error("Create export directory error.");
					eb.publish("entcore.export", exported);
				}
			}
		});
	}

	private void createExportDirectory(String exportPath, String locale, final Handler<String> handler) {
		final String path = exportPath + File.separator +
				I18n.getInstance().translate("workspace.title", locale);
		vertx.fileSystem().mkdir(path, new Handler<AsyncResult<Void>>() {
			@Override
			public void handle(AsyncResult<Void> event) {
				if (event.succeeded()) {
					handler.handle(path);
				} else {
					handler.handle(null);
				}
			}
		});
	}

	@Override
	public void deleteGroups(JsonArray groups) {
		for (Object o : groups) {
			if (!(o instanceof JsonObject)) continue;
			final JsonObject j = (JsonObject) o;
			final JsonObject query = MongoQueryBuilder.build(
				QueryBuilder.start("shared.groupId").is(j.getString("group")));
			JsonArray userShare = new JsonArray();
			for (Object u : j.getArray("users")) {
				JsonObject share = new JsonObject()
						.putString("userId", u.toString())
						.putBoolean("org-entcore-workspace-service-WorkspaceService|copyDocuments", true)
						.putBoolean("org-entcore-workspace-service-WorkspaceService|getDocument", true);
				userShare.addObject(share);
			}
			JsonObject update = new JsonObject()
					.putObject("$addToSet",
							new JsonObject().putObject("shared",
									new JsonObject().putArray("$each", userShare)));
			mongo.update(DocumentDao.DOCUMENTS_COLLECTION, query, update, false, true,
					new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					if (!"ok".equals(event.body().getString("status"))) {
						log.error("Error updating documents with group " +
								j.getString("group") + " : " + event.body().encode());
					} else {
						log.info("Documents with group " + j.getString("group") +
								" updated : " + event.body().getInteger("number"));
					}
				}
			});
		}
	}

	@Override
	public void deleteUsers(JsonArray users) {
		String [] userIds = new String[users.size()];
		for (int i = 0; i < users.size(); i++) {
			JsonObject j = users.get(i);
			userIds[i] = j.getString("id");
		}
		final JsonObject queryDocuments = MongoQueryBuilder.build(QueryBuilder.start("owner").in(userIds));
		deleteFiles(queryDocuments, DocumentDao.DOCUMENTS_COLLECTION);
		final JsonObject queryRacks = MongoQueryBuilder.build(QueryBuilder.start("to").in(userIds));
		deleteFiles(queryRacks, RackDao.RACKS_COLLECTION);
	}

	private void deleteFiles(final JsonObject query, final String collection) {
		mongo.find(collection, query, null,
				new JsonObject().putNumber("file", 1), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				String status = res.body().getString("status");
				JsonArray results = res.body().getArray("results");
				if ("ok".equals(status) && results != null && results.size() > 0) {
					for (Object o : results) {
						if (!(o instanceof JsonObject)) continue;
						final String file = ((JsonObject) o).getString("file");
						FileUtils.gridfsRemoveFile(file, eb, gridfsAddress,
								new Handler<JsonObject>() {

									@Override
									public void handle(JsonObject event) {
										if (event == null) {
											log.error("Error deleting file " + file);
										} else if (!"ok".equals(event.getString("status"))) {
											log.error("Error deleting file " + file + " : " + event.encode());
										}
									}
								});
					}
					mongo.delete(collection, query, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if (!"ok".equals(event.body().getString("status"))) {
								log.error("Error deleting documents : " + event.body().encode());
							} else {
								log.info("Documents deleted : " + event.body().getInteger("number"));
							}
						}
					});
				}
			}
		});
	}

}