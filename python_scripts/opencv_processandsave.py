import os
import argparse
import time
import numpy as np
import cv2


def process(img):

    img = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    _, img = cv2.threshold(img, 150, 255, cv2.THRESH_BINARY_INV)
    kernel = cv2.getStructuringElement(cv2.MORPH_CROSS, (3, 3))
    img = cv2.morphologyEx(img, cv2.MORPH_CLOSE, kernel, iterations=1)

    return img


def load_process_save_images(directory, outputdir):

    if not os.path.exists(directory):
        print("Directory " + directory + " not found!")
        exit(1)

    if not os.path.exists(outputdir):
        print("Output directory " + outputdir + " does not exist. Creating...")
        os.makedirs(outputdir)

    for root, _, files in os.walk(directory, topdown=False):
        for name in files:
            print("Loading image " + os.path.join(root, name))

            img = np.float32(cv2.imread(os.path.join(root, name)))

            img = process(img)

            split_from_ext = name.split(".")
            name_without_extension = ".".join(split_from_ext[:-1])
            print(name_without_extension)

            output_name = os.path.join(outputdir, name_without_extension +
                                       ".processed.jpg")
            print("Saving processed file as " + output_name)

            cv2.imwrite(output_name, img)


if __name__ == "__main__":

    ap = argparse.ArgumentParser()
    ap.add_argument("-d", "--dir", required=True,
                    help="Directory with stored images")
    ap.add_argument('-o', '--output', required=False,
                    help="Directory to store processed images")
    args = vars(ap.parse_args())

    if args['output'] == None:
        OUTPUT_DIR = ".\\opencv_processandsave_output_" + str(int(time.time()))
        print("No output directory supplied. Using " + OUTPUT_DIR)

    else:
        OUTPUT_DIR = ".\\" + args['output']

    DIRECTORY = ".\\" + args['dir']

    load_process_save_images(DIRECTORY, OUTPUT_DIR)
