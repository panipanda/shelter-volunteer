# Project Decisions

## Language

MVP language: English.

Russian localization is postponed to a later phase.

## Visit archive

The project should keep historical visit data.

Upcoming visits and archived visits use the same base visit model.

Participant information must not be publicly displayed in MVP unless explicit consent exists.

Internal participant records may be stored separately from public visit data.

## Volunteer direction

The MVP supports cat volunteering only in the UI.

The architecture should allow future support for dog volunteering without major rewriting.

## Visit calendar

The MVP visit calendar is read-only.

Availability is updated manually by an admin or maintainer.

The website is not the source of truth for volunteer registration during MVP.

Final signup confirmation happens in existing volunteer chats or through a coordinator.

## Timezone

All volunteer visit times are stored and displayed in Europe/Belgrade timezone.

For MVP, visit date and time are stored separately as local date and local time, with explicit timezone field:

- date: YYYY-MM-DD
- time: HH:mm
- timezone: Europe/Belgrade

Technical timestamps such as lastUpdatedAt should include timezone offset.

## Registration

Online registration, cancellation flow, waitlist, notifications and Telegram integration are out of MVP scope.

These features may be added in later phases.

