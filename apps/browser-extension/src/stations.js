const HRS_STATIONS = [
  {
    id: "chmp",
    name: "98,5 FM",
    tagline: "Cogeco — French hockey radio",
    url: "https://playerservices.streamtheworld.com/api/livestream-redirect/CHMPFM.mp3",
    language: "fr"
  }
];

const HRS_DEFAULT_DELAY = 0;

if (typeof self !== "undefined") {
  self.HRS_STATIONS = HRS_STATIONS;
  self.HRS_DEFAULT_DELAY = HRS_DEFAULT_DELAY;
}
