package de.metas.material.event.ddorder;

import java.util.Date;

import org.adempiere.util.Check;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.metas.material.event.MaterialEvent;
import de.metas.material.event.commons.EventDescriptor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
public class DDOrderRequestedEvent implements MaterialEvent
{
	public static final String TYPE = "DDOrderRequestedEvent";

	@NonNull
	EventDescriptor eventDescriptor;

	@NonNull
	Date dateOrdered;

	@NonNull
	DDOrder ddOrder;

	@JsonCreator
	@Builder
	private DDOrderRequestedEvent(
			@JsonProperty("eventDescriptor") @NonNull final EventDescriptor eventDescriptor,
			@JsonProperty("dateOrdered") @NonNull final Date dateOrdered,
			@JsonProperty("ddOrder") @NonNull final DDOrder ddOrder)
	{
		this.eventDescriptor = eventDescriptor;
		this.dateOrdered = dateOrdered;
		this.ddOrder = ddOrder;

		validate();
	}

	private void validate()
	{
		final DDOrder ddOrder = getDdOrder();
		Check.errorIf(ddOrder.getDdOrderId() > 0,
				"The given ddOrderRequestedEvent'd ddOrder may not yet have an ID; ddOrder={}", ddOrder);

		// we need the DDOrder's MaterialDispoGroupId to map the ddOrder its respective candidates after it was created.
		Check.errorIf(ddOrder.getMaterialDispoGroupId() <= 0, "The ddOrder of a DDOrderRequestedEvent needs to have a group id");
	}

}
