package net.hawkengine.core.utilities.deserializers;

import com.google.gson.*;

import net.hawkengine.model.dto.ConversionObject;
import net.hawkengine.model.dto.WsContractDto;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WsContractDeserializer implements JsonDeserializer<WsContractDto> {
	private final List<String> requiredFields;
	private final Gson jsonConverter;

	public WsContractDeserializer() {
		this.requiredFields = new ArrayList<>(
				Arrays.asList("className", "packageName", "methodName", "result", "error", "errorMessage", "args"));
		this.jsonConverter = new GsonBuilder()
				.registerTypeAdapter(ConversionObject.class, new ConversionObjectDeserializer())
				.create();
	}

	@Override
	public WsContractDto deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		JsonObject jsonObject = json.getAsJsonObject();
		for (String fieldName : this.requiredFields) {
			if (jsonObject.get(fieldName) == null) {
				throw new JsonParseException("Required field not found");
			}
		}

		return this.jsonConverter.fromJson(json, WsContractDto.class);
	}
}
