-- Records which build the current game world belongs to. When the app boots and finds a different
-- build marker than the one stored here, it wipes the mountain (a fresh ladder per deployed version)
-- and updates the marker. A plain restart of the same build leaves the marker — and the world — alone.
CREATE TABLE deploy_marker (
    id     INT  PRIMARY KEY,
    marker TEXT NOT NULL
);
